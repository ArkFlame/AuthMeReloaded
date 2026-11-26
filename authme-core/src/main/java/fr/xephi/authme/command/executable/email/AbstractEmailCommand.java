package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.process.Management;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.ValidationService;
import org.bukkit.entity.Player;

import javax.inject.Inject;

/**
 * Common base for the /email subcommands that add or change a player's email address.
 * Holds the services shared by those commands and offers email validation handling.
 */
public abstract class AbstractEmailCommand extends PlayerCommand {

    @Inject
    private Management management;

    @Inject
    private CommonService commonService;

    @Inject
    private ValidationService validationService;

    protected final Management getManagement() {
        return management;
    }

    protected final CommonService getCommonService() {
        return commonService;
    }

    /**
     * Validates an email address and informs the player if it is invalid.
     *
     * @param player the player to inform of a validation error
     * @param email the email address to validate
     * @param errorMessage message to send if the address is invalid
     * @return true if the address is valid, false otherwise
     */
    protected final boolean isEmailValid(Player player, String email, MessageKey errorMessage) {
        if (validationService.validateEmail(email)) {
            return true;
        }
        commonService.send(player, errorMessage);
        return false;
    }
}
