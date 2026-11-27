package fr.xephi.authme.process.register;

import ch.jalu.injector.factory.SingletonStore;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.events.AuthMeAsyncPreRegisterEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.AsynchronousProcess;
import fr.xephi.authme.process.ProcessSharedActions;
import fr.xephi.authme.process.register.executors.RegistrationExecutor;
import fr.xephi.authme.process.register.executors.RegistrationMethod;
import fr.xephi.authme.process.register.executors.RegistrationParameters;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import fr.xephi.authme.util.PlayerUtils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Asynchronous processing of a request for registration.
 */
public class AsyncRegister implements AsynchronousProcess {

    @Inject
    private DataSource database;
    @Inject
    private PlayerCache playerCache;
    @Inject
    private BukkitService bukkitService;
    @Inject
    private CommonService service;
    @Inject
    private SingletonStore<RegistrationExecutor> registrationExecutorFactory;

    /**
     * Performs the registration process for the given player.
     */
    public <P extends RegistrationParameters> void register(RegistrationMethod<P> variant, P parameters) {
        Player player = parameters.getPlayer();
        if (!preRegisterCheck(variant, player)) {
            return;
        }

        RegistrationExecutor<P> executor = registrationExecutorFactory.getSingleton(variant.getExecutorClass());
        if (!executor.isRegistrationAdmitted(parameters)) {
            return;
        }

        if (database.saveAuth(executor.buildPlayerAuth(parameters))) {
            executor.executePostPersistAction(parameters);
        } else {
            service.send(player, MessageKey.ERROR);
        }
    }

    /**
     * Checks if the player is able to register, in that case the {@link AuthMeAsyncPreRegisterEvent} is invoked.
     */
    private boolean preRegisterCheck(RegistrationMethod<?> variant, Player player) {
        // Both the cache and the data source normalize names internally; avoid a lowercase copy here.
        String name = player.getName();
        if (playerCache.isAuthenticated(name)) {
            service.send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
            return false;
        } else if (!service.getProperty(RegistrationSettings.IS_ENABLED)) {
            service.send(player, MessageKey.REGISTRATION_DISABLED);
            return false;
        } else if (database.isAuthAvailable(name)) {
            service.send(player, MessageKey.NAME_ALREADY_REGISTERED);
            return false;
        }

        AuthMeAsyncPreRegisterEvent event = bukkitService.createAndCallEvent(
            isAsync -> new AuthMeAsyncPreRegisterEvent(player, isAsync));
        return event.canRegister()
            && (variant == RegistrationMethod.API_REGISTRATION || isPlayerIpAllowedToRegister(player));
    }

    /**
     * Checks whether the registration threshold has been exceeded for the given player's IP address.
     */
    private boolean isPlayerIpAllowedToRegister(Player player) {
        int maxRegPerIp = service.getProperty(RestrictionSettings.MAX_REGISTRATION_PER_IP);
        if (maxRegPerIp <= 0) {
            return true;
        }

        String ip = PlayerUtils.getPlayerIp(player);
        if (ProcessSharedActions.isExemptFromIpLimit(service, player, ip)) {
            return true;
        }

        List<String> otherAccounts = database.getAllAuthsByIp(ip);
        if (otherAccounts.size() < maxRegPerIp) {
            return true;
        }
        service.send(player, MessageKey.MAX_REGISTER_EXCEEDED, Integer.toString(maxRegPerIp),
            Integer.toString(otherAccounts.size()), String.join(", ", otherAccounts));
        return false;
    }
}
