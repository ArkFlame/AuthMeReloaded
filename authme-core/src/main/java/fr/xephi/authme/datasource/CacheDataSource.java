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
import java.util.stream.Collectors;

public class CacheDataSource implements DataSource, SqlConnectionSource {

    private final ConsoleLogger logger = ConsoleLoggerFactory.get(CacheDataSource.class);

    private final DataSource source;
    private final PlayerCache playerCache;
    private final LoadingCache<String, Optional<PlayerAuth>> cachedAuths;
    private final ListeningExecutorService executorService;

    /**
     * Constructor for CacheDataSource.
     *
     * @param source the source
     * @param playerCache the player cache
     */
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
                method.setAccessible(true);
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

    /**
     * Applies a cache change only when the underlying data source update succeeded.
     *
     * @param succeeded whether the source update succeeded
     * @param cacheChange the cache change to perform on success
     * @return the value of {@code succeeded}
     */
    private boolean updateCacheOnSuccess(boolean succeeded, Runnable cacheChange) {
        if (succeeded) {
            cacheChange.run();
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
        user = user.toLowerCase(Locale.ROOT);
        Optional<PlayerAuth> auth = cachedAuths.getIfPresent(user);
        return auth != null && auth.isPresent()
            ? auth.get().getPassword()
            : source.getPassword(user);
    }

    @Override
    public PlayerAuth getAuth(String user) {
        user = user.toLowerCase(Locale.ROOT);
        return cachedAuths.getUnchecked(user).orElse(null);
    }

    @Override
    public boolean saveAuth(PlayerAuth auth) {
        return updateCacheOnSuccess(source.saveAuth(auth), () -> cachedAuths.refresh(auth.getNickname()));
    }

    @Override
    public boolean updatePassword(PlayerAuth auth) {
        return updateCacheOnSuccess(source.updatePassword(auth), () -> cachedAuths.refresh(auth.getNickname()));
    }

    @Override
    public boolean updatePassword(String user, HashedPassword password) {
        String key = user.toLowerCase(Locale.ROOT);
        return updateCacheOnSuccess(source.updatePassword(key, password), () -> cachedAuths.refresh(key));
    }

    @Override
    public boolean updateSession(PlayerAuth auth) {
        return updateCacheOnSuccess(source.updateSession(auth), () -> cachedAuths.refresh(auth.getNickname()));
    }

    @Override
    public boolean updateQuitLoc(final PlayerAuth auth) {
        return updateCacheOnSuccess(source.updateQuitLoc(auth), () -> cachedAuths.refresh(auth.getNickname()));
    }

    @Override
    public Set<String> getRecordsToPurge(long until) {
        return source.getRecordsToPurge(until);
    }

    @Override
    public boolean removeAuth(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return updateCacheOnSuccess(source.removeAuth(key), () -> cachedAuths.invalidate(key));
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
        return updateCacheOnSuccess(source.updateEmail(auth), () -> cachedAuths.refresh(auth.getNickname()));
    }

    @Override
    public boolean updatePremiumUuid(PlayerAuth auth) {
        return updateCacheOnSuccess(source.updatePremiumUuid(auth),
            () -> cachedAuths.put(auth.getNickname(), Optional.of(auth)));
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
        cachedAuths.invalidateAll(banned);
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
        source.setLogged(user.toLowerCase(Locale.ROOT));
    }

    @Override
    public void setUnlogged(final String user) {
        source.setUnlogged(user.toLowerCase(Locale.ROOT));
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
        return updateCacheOnSuccess(source.updateRealName(user, realName), () -> cachedAuths.refresh(user));
    }

    @Override
    public DataSourceValue<String> getEmail(String user) {
        // Use the same normalized key as getAuth/getPassword: otherwise a mixed-case
        // name creates a second cache entry and an extra DB load for the same account.
        String key = user.toLowerCase(Locale.ROOT);
        Optional<PlayerAuth> auth = cachedAuths.getUnchecked(key);
        if (auth.isPresent()) {
            return DataSourceValueImpl.of(auth.get().getEmail());
        }
        return DataSourceValueImpl.unknownRow();
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
        return updateCacheOnSuccess(source.setTotpKey(user, totpKey), () -> cachedAuths.refresh(user));
    }

    @Override
    public void invalidateCache(String playerName) {
        cachedAuths.invalidate(playerName);
    }

}
