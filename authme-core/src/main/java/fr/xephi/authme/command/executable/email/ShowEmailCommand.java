package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.command.PlayerCommand;
import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.SecuritySettings;
import fr.xephi.authme.util.Utils;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;

/**
 * Show email command.
 */
public class ShowEmailCommand extends PlayerCommand {

    @Inject
    private CommonService commonService;

    @Inject
    private PlayerCache playerCache;

    @Override
    public void runCommand(Player player, List<String> arguments) {
        PlayerAuth auth = playerCache.getAuth(player.getName());
        final String email = auth == null ? null : auth.getEmail();
        if (Utils.isEmailEmpty(email)) {
            commonService.send(player, MessageKey.SHOW_NO_EMAIL);
            return;
        }

        String emailToShow = commonService.getProperty(SecuritySettings.USE_EMAIL_MASKING)
            ? emailMask(email) : email;
        commonService.send(player, MessageKey.EMAIL_SHOW, emailToShow);
    }

    private String emailMask(String email) {
        String[] parts = email.split("@");
        // A value stored by the default validator may lack a local part or a domain
        // (e.g. "user@" or "@example.com"); split() drops trailing empties, so pad.
        String localPart = parts.length > 0 ? parts[0] : "";
        String domain = parts.length > 1 ? parts[1] : "";
        int idLength = Math.min(localPart.length(), localPart.length() / 3 + 1); // required length >= 1
        int domainStart = domain.length() / 3; // required length >= 0
        return localPart.substring(0, idLength) + "***@***" + domain.substring(domainStart);
    }
}
