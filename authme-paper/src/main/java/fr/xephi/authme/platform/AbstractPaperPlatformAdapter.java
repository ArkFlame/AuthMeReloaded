package fr.xephi.authme.platform;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.command.CommandDescription;
import fr.xephi.authme.command.CommandHandler;
import fr.xephi.authme.listener.EntityAirChangeListener;
import fr.xephi.authme.listener.EntityPickupItemListener;
import fr.xephi.authme.listener.PaperDialogFlowListener;
import fr.xephi.authme.listener.PaperLoginValidationListener;
import fr.xephi.authme.listener.PaperProxyAutoLoginListener;
import fr.xephi.authme.listener.PlayerOpenSignListener;
import fr.xephi.authme.listener.PlayerSwapHandItemsListener;
import fr.xephi.authme.process.register.RegisterSecondaryArgument;
import fr.xephi.authme.process.register.RegistrationType;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared platform adapter behavior for Paper-derived servers.
 */
public abstract class AbstractPaperPlatformAdapter extends AbstractSpigotPlatformAdapter {

    private static final boolean DIALOG_AVAILABLE = hasDialogApi();

    /** Paper API classes every Paper-derived platform requires. */
    private static final String[] PAPER_API_CLASSES = {
        "io.papermc.paper.event.player.AsyncChatEvent",
        "io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent",
        "io.papermc.paper.event.player.PlayerOpenSignEvent"
    };

    private final Map<UUID, CompletableFuture<Boolean>> pendingTeleports = new ConcurrentHashMap<>();

    @Override
    public void teleportPlayer(Player player, Location location) {
        UUID playerId = player.getUniqueId();
        // compute() atomically reads the pending teleport and installs the new one, so concurrent
        // teleports for the same player are applied in call order instead of racing each other.
        CompletableFuture<Boolean> teleport = pendingTeleports.compute(playerId, (ignoredId, previous) -> {
            if (previous == null) {
                return player.teleportAsync(location);
            }
            CompletableFuture<Boolean> chained = new CompletableFuture<>();
            previous.whenComplete((ignoredResult, ignoredError) -> runChainedTeleport(player, location, chained));
            return chained;
        });
        teleport.whenComplete((ignoredResult, ignoredError) -> pendingTeleports.remove(playerId, teleport));
    }

    private void runChainedTeleport(Player player, Location location, CompletableFuture<Boolean> result) {
        if (player.getScheduler().run(JavaPlugin.getPlugin(AuthMe.class),
            ignored -> player.teleportAsync(location).whenComplete((teleportResult, error) -> {
                if (error == null) {
                    result.complete(teleportResult);
                } else {
                    result.completeExceptionally(error);
                }
            }),
            () -> result.complete(false)) == null) {
            result.complete(false);
        }
    }

    @Override
    public Location getPlayerRespawnLocation(Player player) {
        return player.getRespawnLocation();
    }

    @Override
    public String getKickReason(PlayerKickEvent event) {
        return PlainTextComponentSerializer.plainText().serialize(event.reason());
    }

    @Override
    public boolean isDialogSupported() {
        return DIALOG_AVAILABLE;
    }

    @Override
    public void showLoginDialog(Player player, DialogWindowSpec dialog) {
        PaperDialogHelper.showLoginDialog(player, dialog);
    }

    @Override
    public void showTotpDialog(Player player, DialogWindowSpec dialog) {
        PaperDialogHelper.showTotpDialog(player, dialog);
    }

    @Override
    public void showRegisterDialog(Player player, RegistrationType type,
                                   RegisterSecondaryArgument secondArg, DialogWindowSpec dialog) {
        PaperDialogHelper.showRegisterDialog(player, type, secondArg, dialog);
    }

    @Override
    public void showRecoveryCodeDialog(Player player, DialogWindowSpec dialog) {
        PaperDialogHelper.showRecoveryCodeDialog(player, dialog);
    }

    @Override
    public void showNewPasswordDialog(Player player, DialogWindowSpec dialog) {
        PaperDialogHelper.showNewPasswordDialog(player, dialog);
    }

    @Override
    public void closeDialog(Player player) {
        if (DIALOG_AVAILABLE) {
            PaperDialogHelper.closeDialog(player);
        }
    }

    @Override
    public void registerCommands(AuthMe plugin, CommandHandler commandHandler, Collection<CommandDescription> commands) {
        new PaperBrigadierCommandRegistrar(commandHandler::processCommand).registerCommands(plugin, commands);
    }

    @Override
    public List<Class<? extends Listener>> getListeners() {
        return EventRegistrationAdapter.combineListeners(
            EventRegistrationAdapter.getCommonListeners(),
            List.of(PlayerSwapHandItemsListener.class, EntityAirChangeListener.class, EntityPickupItemListener.class),
            getPlatformListeners());
    }

    /**
     * Returns the listeners specific to the concrete Paper-derived platform.
     *
     * @return the platform-specific listeners
     */
    protected List<Class<? extends Listener>> getPlatformListeners() {
        return List.of();
    }

    /**
     * Builds the listener list shared by Paper-derived platforms.
     *
     * @param chatListener the platform's chat listener
     * @param spawnListener the platform's spawn-location listener
     * @return the platform listeners in registration order
     */
    protected final List<Class<? extends Listener>> getPaperListeners(
            Class<? extends Listener> chatListener, Class<? extends Listener> spawnListener) {
        return List.<Class<? extends Listener>>of(
            chatListener,
            PaperDialogFlowListener.class,
            PaperProxyAutoLoginListener.class,
            spawnListener,
            PaperLoginValidationListener.class,
            PlayerOpenSignListener.class);
    }

    /**
     * Checks the shared Paper API classes, plus any platform-specific ones supplied by the caller.
     *
     * @param errorMessage the message to return when a class is missing
     * @param platformClasses additional classes the concrete platform requires
     * @return the compatibility error, or {@code null} when all classes are present
     */
    protected final String getPaperCompatibilityError(String errorMessage, String... platformClasses) {
        String platformError = getCompatibilityError(errorMessage, platformClasses);
        return platformError != null ? platformError : getCompatibilityError(errorMessage, PAPER_API_CLASSES);
    }

    @Override
    public boolean isProxyForwardingEnabled() {
        return super.isProxyForwardingEnabled() || isPaperVelocityForwardingEnabled();
    }

    private static boolean hasDialogApi() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog", false, AbstractPaperPlatformAdapter.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean isPaperVelocityForwardingEnabled() {
        try {
            // io.papermc.paper.configuration.GlobalConfiguration is Paper 1.18+
            Class<?> cfg = Class.forName(
                "io.papermc.paper.configuration.GlobalConfiguration",
                false, AbstractPaperPlatformAdapter.class.getClassLoader());
            Object instance = cfg.getMethod("get").invoke(null);
            Object proxies = cfg.getField("proxies").get(instance);
            Object velocity = proxies.getClass().getField("velocity").get(proxies);
            Object enabled = velocity.getClass().getField("enabled").get(velocity);
            return Boolean.TRUE.equals(enabled);
        } catch (Exception ignored) {
            return false;
        }
    }
}
