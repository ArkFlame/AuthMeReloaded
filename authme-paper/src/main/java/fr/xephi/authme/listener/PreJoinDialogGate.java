package fr.xephi.authme.listener;

import fr.xephi.authme.data.ProxySessionManager;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.service.PendingPremiumCache;
import fr.xephi.authme.service.SessionService;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import org.bukkit.Bukkit;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Decides whether a connection still needs a blocking pre-join dialog.
 */
final class PreJoinDialogGate {

    // MC 1.21.6 (protocol 771) introduced the dialog / custom-click packets required for pre-join dialogs
    private static final int DIALOG_MIN_PROTOCOL = 771;

    private PreJoinDialogGate() {
    }

    static boolean shouldSkipDialogs(String normalizedName, PlayerConfigurationConnection connection,
                                     PlayerCache playerCache, ProxySessionManager proxySessionManager,
                                     SessionService sessionService, DataSource dataSource,
                                     PendingPremiumCache pendingPremiumCache) {
        if (playerCache.isAuthenticated(normalizedName)) {
            return true;
        }

        if (hasValidProxySession(normalizedName, proxySessionManager, dataSource, pendingPremiumCache)) {
            return true;
        }

        InetSocketAddress clientAddress = connection.getClientAddress();
        String ipAddress = clientAddress == null ? null : clientAddress.getAddress().getHostAddress();
        if (sessionService.hasValidSession(normalizedName, ipAddress)) {
            return true;
        }

        return !isClientDialogCapable(connection.getProfile().getId());
    }

    /** True when a queued proxy login exists and either needs no verification or was verified for this name. */
    static boolean hasValidProxySession(String normalizedName, ProxySessionManager proxySessionManager,
                                        DataSource dataSource, PendingPremiumCache pendingPremiumCache) {
        if (!proxySessionManager.shouldResumeSession(normalizedName)) {
            return false;
        }
        ProxySessionManager.ProxyLoginRequest request = proxySessionManager.getLoginRequest(normalizedName);
        return request != null && (request.verifiedPremiumUuid() == null
            || isProxyPremiumRequestValid(normalizedName, request, dataSource, pendingPremiumCache));
    }

    static boolean isProxyPremiumRequestValid(String normalizedName, ProxySessionManager.ProxyLoginRequest request,
                                              DataSource dataSource, PendingPremiumCache pendingPremiumCache) {
        UUID verifiedUuid = request.verifiedPremiumUuid();
        if (verifiedUuid == null) {
            return true;
        }
        PlayerAuth auth = dataSource.getAuth(normalizedName);
        if (auth == null) {
            return false;
        }
        UUID expectedUuid = auth.isPremium()
            ? auth.getPremiumUuid()
            : pendingPremiumCache.getPendingUuid(normalizedName);
        return verifiedUuid.equals(expectedUuid);
    }

    private static boolean isClientDialogCapable(UUID playerId) {
        try {
            if (Bukkit.getPluginManager().getPlugin("ViaVersion") == null) {
                return true;
            }
            Class<?> viaApiClass = Class.forName("com.viaversion.viaversion.api.ViaAPI");
            Object api = Class.forName("com.viaversion.viaversion.api.Via").getMethod("getAPI").invoke(null);
            int version = (int) viaApiClass.getMethod("getPlayerVersion", UUID.class).invoke(api, playerId);
            return version >= DIALOG_MIN_PROTOCOL;
        } catch (Exception ignored) {
            return true;
        }
    }
}
