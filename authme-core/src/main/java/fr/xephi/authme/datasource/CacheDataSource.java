package fr.xephi.authme.datasource;

import ch.jalu.datasourcecolumns.data.DataSourceValue;
import ch.jalu.datasourcecolumns.data.DataSourceValueImpl;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import fr.xephi.authme.ConsoleLogger;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.output.ConsoleLoggerFactory;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.util.Utils;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CacheDataSource implements DataSource, SqlConnectionSource {

    /** Upper bound for the auth cache; a miss simply reloads from the underlying data source. */
    static final int MAX_CACHED_AUTHS = 10_000;

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(CacheDataSource.class);

    private final DataSource source;
    private final PlayerCache playerCache;
    private final LoadingCache<String, Optional<PlayerAuth>> cachedAuths;
    private final ListeningExecutorService executorService;

    public CacheDataSource(DataSource source, PlayerCache playerCache) {
        this(source, playerCache, false);
    }

    /**
     * Constructor for CacheDataSource.
     *
     * @param source the source
     * @param playerCache the player cache
     * @param useVirtualThreads whether to attempt the use of virtual threads
     */
    public CacheDataSource(DataSource source, PlayerCache playerCache, boolean useVirtualThreads) {
        this.source = source;
        this.playerCache = playerCache;

        executorService = createExecutorService(useVirtualThreads, Executors.class);
        cachedAuths = CacheBuilder.newBuilder()
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(MAX_CACHED_AUTHS)
            .build(new CacheLoader<String, Optional<PlayerAuth>>() {
                @Override
                public Optional<PlayerAuth> load(String key) {
                    return Optional.ofNullable(source.getAuth(key));
                }

                @Override
                public ListenableFuture<Optional<PlayerAuth>> reload(final String key, Optional<PlayerAuth> oldValue) {
                    return executorService.submit(() -> load(key));
                }
            });
    }

    // Adapted from HaHaWTH/AuthMeReReloaded: virtual-thread cache executor.
    /**
     * Creates the executor for asynchronous cache reloads. Kept static and package-private so the
     * fallback can be tested without depending on the JDK version of the test JVM.
     *
     * @param useVirtualThreads whether a virtual-thread executor should be attempted
     * @param executorsClass the class to inspect reflectively (Executors.class in production)
     * @return the listening executor service
     */
    static ListeningExecutorService createExecutorService(
            boolean useVirtualThreads, Class<?> executorsClass) {
        if (useVirtualThreads) {
            try {
                Method method = executorsClass.getMethod("newVirtualThreadPerTaskExecutor");
                ExecutorService executor = (ExecutorService) method.invoke(null);
                return MoreExecutors.listeningDecorator(executor);
            } catch (Exception e) {
                // Java < 21 or a restricted JDK: fall through to the cached pool
            }
        }
        return MoreExecutors.listeningDecorator(
            Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("AuthMe-CacheLoader")
                .build())
        );
    }

    public LoadingCache<String, Optional<PlayerAuth>> getCachedAuths() {
        return cachedAuths;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Applies a cache change only when the underlying data source update succeeded. On failure the
     * entry is dropped instead, because the cache may no longer reflect the database state.
     */
    private boolean updateCacheOnSuccess(boolean succeeded, String key, Consumer<String> cacheChange) {
        if (succeeded) {
            cacheChange.accept(key);
        } else if (key != null) {
            cachedAuths.invalidate(key);
        }
        return succeeded;
    }

    @Override
    public void reload() {
        source.reload();
    }

    @Override
    public boolean isAuthAvailable(String user) {
        return getAuth(user) != null;
    }

    @Override
    public HashedPassword getPassword(String user) {
        String key = normalize(user);
        Optional<PlayerAuth> auth = cachedAuths.getIfPresent(key);
        return auth != null && auth.isPresent() ? auth.get().getPassword() : source.getPassword(key);
    }

    @Override
    public PlayerAuth getAuth(String user) {
        return cachedAuths.getUnchecked(normalize(user)).orElse(null);
    }

    @Override
    public boolean saveAuth(PlayerAuth auth) {
        return updateCacheOnSuccess(source.saveAuth(auth), auth.getNickname(), cachedAuths::invalidate);
    }

    @Override
    public boolean updatePassword(PlayerAuth auth) {
        return updateCacheOnSuccess(source.updatePassword(auth), auth.getNickname(), cachedAuths::invalidate);
    }

    @Override
    public boolean updatePassword(String user, HashedPassword password) {
        String key = normalize(user);
        return updateCacheOnSuccess(source.updatePassword(key, password), key, cachedAuths::invalidate);
    }

    @Override
    public boolean updateSession(PlayerAuth auth) {
        return updateCacheOnSuccess(source.updateSession(auth), auth.getNickname(), key -> {
            // The login flow mutates the cached instance itself before persisting it: the cache already
            // holds exactly what was written, so reloading it would only cost an extra SELECT per login.
            Optional<PlayerAuth> cached = cachedAuths.getIfPresent(key);
            if (cached == null || !cached.isPresent() || cached.get() != auth) {
                cachedAuths.refresh(key);
            }
        });
    }

    @Override
    public boolean updateQuitLoc(final PlayerAuth auth) {
        return updateCacheOnSuccess(source.updateQuitLoc(auth), auth.getNickname(), cachedAuths::refresh);
    }

    @Override
    public Set<String> getRecordsToPurge(long until) {
        return source.getRecordsToPurge(until);
    }

    @Override
    public boolean removeAuth(String name) {
        String key = normalize(name);
        return updateCacheOnSuccess(source.removeAuth(key), key, cachedAuths::invalidate);
    }

    @Override
    public void closeConnection() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.logException("Could not close executor service:", e);
        }
        cachedAuths.invalidateAll();
        source.closeConnection();
    }

    @Override
    public boolean updateEmail(final PlayerAuth auth) {
        return updateCacheOnSuccess(source.updateEmail(auth), auth.getNickname(), cachedAuths::refresh);
    }

    @Override
    public boolean updatePremiumUuid(PlayerAuth auth) {
        return updateCacheOnSuccess(source.updatePremiumUuid(auth), auth.getNickname(),
            key -> cachedAuths.put(key, Optional.of(auth)));
    }

    @Override
    public List<String> getPremiumUsernames() {
        return source.getPremiumUsernames();
    }

    @Override
    public List<String> getAllAuthsByIp(String ip) {
        return source.getAllAuthsByIp(ip);
    }

    @Override
    public int countAuthsByEmail(String email) {
        return source.countAuthsByEmail(email);
    }

    @Override
    public void purgeRecords(Collection<String> banned) {
        source.purgeRecords(banned);
        banned.forEach(name -> cachedAuths.invalidate(normalize(name)));
    }

    @Override
    public DataSourceType getType() {
        return source.getType();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (source instanceof SqlConnectionSource) {
            return ((SqlConnectionSource) source).getConnection();
        }
        throw new SQLException("The configured data source does not support raw SQL connections");
    }

    @Override
    public boolean isLogged(String user) {
        return source.isLogged(user);
    }

    @Override
    public void setLogged(final String user) {
        source.setLogged(normalize(user));
    }

    @Override
    public void setUnlogged(final String user) {
        source.setUnlogged(normalize(user));
    }

    @Override
    public boolean hasSession(final String user) {
        return source.hasSession(user);
    }

    @Override
    public void grantSession(final String user) {
        source.grantSession(user);
    }

    @Override
    public void revokeSession(final String user) {
        source.revokeSession(user);
    }

    @Override
    public void purgeLogged() {
        source.purgeLogged();
        cachedAuths.invalidateAll();
    }

    @Override
    public int getAccountsRegistered() {
        return source.getAccountsRegistered();
    }

    @Override
    public boolean updateRealName(String user, String realName) {
        return updateCacheOnSuccess(source.updateRealName(user, realName), normalize(user), cachedAuths::refresh);
    }

    @Override
    public DataSourceValue<String> getEmail(String user) {
        // Normalized like getAuth: a mixed-case name must not create a second cache entry.
        PlayerAuth auth = getAuth(user);
        return auth != null ? DataSourceValueImpl.of(auth.getEmail()) : DataSourceValueImpl.unknownRow();
    }

    @Override
    public List<PlayerAuth> getAllAuths() {
        return source.getAllAuths();
    }

    @Override
    public List<String> getLoggedPlayersWithEmptyMail() {
        return playerCache.getCache().values().stream()
            .filter(auth -> Utils.isEmailEmpty(auth.getEmail()))
            .map(PlayerAuth::getRealName)
            .collect(Collectors.toList());
    }

    @Override
    public List<PlayerAuth> getRecentlyLoggedInPlayers() {
        return source.getRecentlyLoggedInPlayers();
    }

    @Override
    public boolean setTotpKey(String user, String totpKey) {
        return updateCacheOnSuccess(source.setTotpKey(user, totpKey), normalize(user), cachedAuths::invalidate);
    }

    @Override
    public void invalidateCache(String playerName) {
        cachedAuths.invalidate(normalize(playerName));
    }

}
