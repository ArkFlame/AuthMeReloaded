package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.service.PremiumService;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.util.List;

/** Common base for admin commands that change the premium state of a player by name. */
public abstract class SetPremiumStateAdminCommand implements ExecutableCommand {

    @Inject
    private PremiumService premiumService;

    @Override
    public final void executeCommand(CommandSender sender, List<String> arguments) {
        updatePremiumState(sender, arguments.get(0));
    }

    /** Applies the premium state change to the named player. */
    protected abstract void updatePremiumState(CommandSender sender, String playerName);

    protected PremiumService getPremiumService() {
        return premiumService;
    }
}
