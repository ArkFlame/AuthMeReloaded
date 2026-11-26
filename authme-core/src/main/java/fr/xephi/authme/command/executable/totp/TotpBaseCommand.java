package fr.xephi.authme.command.executable.totp;

import fr.xephi.authme.command.executable.BaseHelpCommand;

/**
 * Base command for /totp, showing information about the child commands.
 */
public class TotpBaseCommand extends BaseHelpCommand {

    @Override
    protected String getBaseLabel() {
        return "totp";
    }
}
