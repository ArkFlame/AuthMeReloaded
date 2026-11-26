package fr.xephi.authme.command.executable.email;

import fr.xephi.authme.command.executable.BaseHelpCommand;

/**
 * Base command for /email, showing information about the child commands.
 */
public class EmailBaseCommand extends BaseHelpCommand {

    @Override
    protected String getBaseLabel() {
        return "email";
    }
}
