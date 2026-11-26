package fr.xephi.authme.command;

import com.google.common.collect.ImmutableList;
import fr.xephi.authme.command.executable.HelpCommand;
import fr.xephi.authme.command.executable.authme.AccountsCommand;
import fr.xephi.authme.command.executable.authme.AuthMeCommand;
import fr.xephi.authme.command.executable.authme.BackupCommand;
import fr.xephi.authme.command.executable.authme.ChangePasswordAdminCommand;
import fr.xephi.authme.command.executable.authme.ConverterCommand;
import fr.xephi.authme.command.executable.authme.FirstSpawnCommand;
import fr.xephi.authme.command.executable.authme.ForceLoginCommand;
import fr.xephi.authme.command.executable.authme.GetEmailCommand;
import fr.xephi.authme.command.executable.authme.GetIpCommand;
import fr.xephi.authme.command.executable.authme.LastLoginCommand;
import fr.xephi.authme.command.executable.authme.PurgeBannedPlayersCommand;
import fr.xephi.authme.command.executable.authme.PurgeCommand;
import fr.xephi.authme.command.executable.authme.PurgeLastPositionCommand;
import fr.xephi.authme.command.executable.authme.PurgePlayerCommand;
import fr.xephi.authme.command.executable.authme.RecentPlayersCommand;
import fr.xephi.authme.command.executable.authme.RegisterAdminCommand;
import fr.xephi.authme.command.executable.authme.ReloadCommand;
import fr.xephi.authme.command.executable.authme.SetEmailCommand;
import fr.xephi.authme.command.executable.authme.SetFirstSpawnCommand;
import fr.xephi.authme.command.executable.authme.SetSpawnCommand;
import fr.xephi.authme.command.executable.authme.SpawnCommand;
import fr.xephi.authme.command.executable.authme.SwitchAntiBotCommand;
import fr.xephi.authme.command.executable.authme.TotpDisableAdminCommand;
import fr.xephi.authme.command.executable.authme.TotpViewStatusCommand;
import fr.xephi.authme.command.executable.authme.UnregisterAdminCommand;
import fr.xephi.authme.command.executable.authme.UpdateHelpMessagesCommand;
import fr.xephi.authme.command.executable.authme.UuidCommand;
import fr.xephi.authme.command.executable.authme.VersionCommand;
import fr.xephi.authme.command.executable.authme.debug.DebugCommand;
import fr.xephi.authme.command.executable.captcha.CaptchaCommand;
import fr.xephi.authme.command.executable.changepassword.ChangePasswordCommand;
import fr.xephi.authme.command.executable.email.AddEmailCommand;
import fr.xephi.authme.command.executable.email.ChangeEmailCommand;
import fr.xephi.authme.command.executable.email.ConfirmEmailCommand;
import fr.xephi.authme.command.executable.email.EmailBaseCommand;
import fr.xephi.authme.command.executable.email.EmailSetPasswordCommand;
import fr.xephi.authme.command.executable.email.ProcessCodeCommand;
import fr.xephi.authme.command.executable.email.RecoverEmailCommand;
import fr.xephi.authme.command.executable.email.ShowEmailCommand;
import fr.xephi.authme.command.executable.login.LoginCommand;
import fr.xephi.authme.command.executable.logout.LogoutCommand;
import fr.xephi.authme.command.executable.register.RegisterCommand;
import fr.xephi.authme.command.executable.totp.AddTotpCommand;
import fr.xephi.authme.command.executable.totp.ConfirmTotpCommand;
import fr.xephi.authme.command.executable.totp.RemoveTotpCommand;
import fr.xephi.authme.command.executable.totp.TotpBaseCommand;
import fr.xephi.authme.command.executable.totp.TotpCodeCommand;
import fr.xephi.authme.command.executable.unregister.UnregisterCommand;
import fr.xephi.authme.command.executable.verification.VerificationCommand;
import fr.xephi.authme.command.executable.authme.SetFreemiumAdminCommand;
import fr.xephi.authme.command.executable.authme.SetPremiumAdminCommand;
import fr.xephi.authme.command.executable.premium.PremiumCommand;
import fr.xephi.authme.command.executable.premium.FreemiumCommand;
import fr.xephi.authme.permission.AdminPermission;
import fr.xephi.authme.permission.DebugSectionPermissions;
import fr.xephi.authme.permission.PermissionNode;
import fr.xephi.authme.permission.PlayerPermission;

import java.util.Collection;
import java.util.List;

/**
 * Initializes all available AuthMe commands.
 */
public class CommandInitializer {

    private static final boolean OPTIONAL = true;
    private static final boolean MANDATORY = false;

    private final List<CommandDescription> commands;

    public CommandInitializer() {
        commands = ImmutableList.<CommandDescription>builder()
            .add(buildAuthMeBaseCommand())
            .add(buildEmailBaseCommand())
            .addAll(buildAccountBaseCommands())
            .add(buildTotpBaseCommand())
            .addAll(buildMiscBaseCommands())
            .build();

        setHelpOnAllBases(commands);
    }

    /**
     * Returns the description of all AuthMe commands.
     *
     * @return the command descriptions
     */
    public List<CommandDescription> getCommands() {
        return commands;
    }

    /**
     * Builds the base commands for logging in, logging out, registering and changing passwords.
     *
     * @return the account-related base commands
     */
    private List<CommandDescription> buildAccountBaseCommands() {
        return ImmutableList.of(
            register(null, LoginCommand.class, labels("login", "l", "log"), "Login command",
                "Command to log in using AuthMeReloaded.", PlayerPermission.LOGIN,
                arg("password", "Login password", MANDATORY)),
            register(null, LogoutCommand.class, labels("logout"), "Logout command",
                "Command to logout using AuthMeReloaded.", PlayerPermission.LOGOUT),
            register(null, RegisterCommand.class, labels("register", "reg"), "Register an account",
                "Command to register using AuthMeReloaded.", PlayerPermission.REGISTER,
                arg("password", "Password", OPTIONAL), arg("verifyPassword", "Verify password", OPTIONAL)),
            register(null, UnregisterCommand.class, labels("unregister", "unreg"), "Unregister an account",
                "Command to unregister using AuthMeReloaded.", PlayerPermission.UNREGISTER,
                arg("password", "Password", MANDATORY)),
            register(null, ChangePasswordCommand.class, labels("changepassword", "changepass", "cp"),
                "Change password of an account", "Command to change your password using AuthMeReloaded.",
                PlayerPermission.CHANGE_PASSWORD,
                arg("oldPassword", "Old password", MANDATORY), arg("newPassword", "New password", MANDATORY)));
    }

    /**
     * Builds the captcha, verification, premium and freemium base commands.
     *
     * @return the remaining player-facing base commands
     */
    private List<CommandDescription> buildMiscBaseCommands() {
        return ImmutableList.of(
            register(null, CaptchaCommand.class, labels("captcha"), "Captcha command",
                "Captcha command for AuthMeReloaded.", PlayerPermission.CAPTCHA,
                arg("captcha", "The Captcha", MANDATORY)),
            register(null, VerificationCommand.class, labels("verification"), "Verification command",
                "Command to complete the verification process for AuthMeReloaded.",
                PlayerPermission.VERIFICATION_CODE, arg("code", "The code", MANDATORY)),
            register(null, PremiumCommand.class, labels("premium"), "Enable premium mode",
                "Enables premium mode: skip authentication with a verified Mojang account.",
                PlayerPermission.USE_PREMIUM),
            register(null, FreemiumCommand.class, labels("freemium"), "Disable premium mode",
                "Disables premium mode and restores password-based authentication.",
                PlayerPermission.USE_FREEMIUM));
    }

    /**
     * Creates a command description object for {@code /authme} including its children.
     *
     * @return the authme base command description
     */
    private CommandDescription buildAuthMeBaseCommand() {
        CommandDescription authmeBase = register(null, AuthMeCommand.class, labels("authme"),
            "AuthMe op commands", "The main AuthMeRemastered command. The root for all admin commands.", null);

        registerAccountCommands(authmeBase);
        registerPlayerInfoCommands(authmeBase);
        registerSpawnCommands(authmeBase);
        registerDataCommands(authmeBase);
        registerUtilityCommands(authmeBase);
        registerPremiumCommands(authmeBase);
        registerDebugCommand(authmeBase);

        return authmeBase;
    }

    /** Registers the /authme subcommands for adding, removing and updating accounts. */
    private void registerAccountCommands(CommandDescription parent) {
        register(parent, RegisterAdminCommand.class, labels("register", "reg", "r"), "Register a player",
            "Register the specified player with the specified password.", AdminPermission.REGISTER,
            arg("player", "Player name", MANDATORY), arg("password", "Password", MANDATORY));

        register(parent, UnregisterAdminCommand.class, labels("unregister", "unreg", "unr"), "Unregister a player",
            "Unregister the specified player.", AdminPermission.UNREGISTER,
            arg("player", "Player name", MANDATORY));

        register(parent, ForceLoginCommand.class, labels("forcelogin", "login"), "Enforce login player",
            "Enforce the specified player to login.", AdminPermission.FORCE_LOGIN,
            arg("player", "Online player name", OPTIONAL));

        register(parent, ChangePasswordAdminCommand.class,
            labels("password", "changepassword", "changepass", "cp"), "Change a player's password",
            "Change the password of a player.", AdminPermission.CHANGE_PASSWORD,
            arg("player", "Player name", MANDATORY), arg("pwd", "New password", MANDATORY));
    }

    /** Registers the /authme subcommands that display information about players. */
    private void registerPlayerInfoCommands(CommandDescription parent) {
        register(parent, LastLoginCommand.class, labels("lastlogin", "ll"), "Player's last login",
            "View the date of the specified players last login.", AdminPermission.LAST_LOGIN,
            arg("player", "Player name", OPTIONAL));

        register(parent, AccountsCommand.class, labels("accounts", "account"), "Display player accounts",
            "Display all accounts of a player by his player name or IP.", AdminPermission.ACCOUNTS,
            arg("player", "Player name or IP", OPTIONAL));

        register(parent, GetEmailCommand.class, labels("email", "mail", "getemail", "getmail"),
            "Display player's email", "Display the email address of the specified player if set.",
            AdminPermission.GET_EMAIL, arg("player", "Player name", OPTIONAL));

        register(parent, SetEmailCommand.class, labels("setemail", "setmail", "chgemail", "chgmail"),
            "Change player's email", "Change the email address of the specified player.",
            AdminPermission.CHANGE_EMAIL, arg("player", "Player name", MANDATORY),
            arg("email", "Player email", MANDATORY));

        register(parent, GetIpCommand.class, labels("getip", "ip"), "Get player's IP",
            "Get the IP address of the specified online player.", AdminPermission.GET_IP,
            arg("player", "Player name", MANDATORY));

        register(parent, UuidCommand.class, labels("uuid"), "Show player's UUIDs",
            "Show the offline, Mojang and Floodgate UUID of the specified player.", AdminPermission.UUID,
            arg("player", "Player name", MANDATORY));

        register(parent, TotpViewStatusCommand.class, labels("totp", "2fa"), "See if a player has enabled TOTP",
            "Returns whether the specified player has enabled two-factor authentication.",
            AdminPermission.VIEW_TOTP_STATUS, arg("player", "Player name", MANDATORY));

        register(parent, TotpDisableAdminCommand.class,
            labels("disabletotp", "disable2fa", "deletetotp", "delete2fa"), "Delete TOTP token of a player",
            "Disable two-factor authentication for a player.", AdminPermission.DISABLE_TOTP,
            arg("player", "Player name", MANDATORY));
    }

    /** Registers the /authme subcommands for teleporting to and changing spawns. */
    private void registerSpawnCommands(CommandDescription parent) {
        register(parent, SpawnCommand.class, labels("spawn", "home"), "Teleport to spawn",
            "Teleport to the spawn.", AdminPermission.SPAWN);

        register(parent, SetSpawnCommand.class, labels("setspawn", "chgspawn"), "Change the spawn",
            "Change the player's spawn to your current position.", AdminPermission.SET_SPAWN);

        register(parent, FirstSpawnCommand.class, labels("firstspawn", "firsthome"), "Teleport to first spawn",
            "Teleport to the first spawn.", AdminPermission.FIRST_SPAWN);

        register(parent, SetFirstSpawnCommand.class, labels("setfirstspawn", "chgfirstspawn"),
            "Change the first spawn", "Change the first player's spawn to your current position.",
            AdminPermission.SET_FIRST_SPAWN);
    }

    /** Registers the /authme subcommands for purging and backing up data. */
    private void registerDataCommands(CommandDescription parent) {
        register(parent, PurgeCommand.class, labels("purge", "delete"), "Purge old data",
            "Purge old AuthMeRemastered data longer than the specified number of days ago.", AdminPermission.PURGE,
            arg("days", "Number of days", MANDATORY));

        register(parent, PurgePlayerCommand.class, labels("purgeplayer"), "Purges the data of one player",
            "Purges data of the given player.", AdminPermission.PURGE_PLAYER,
            arg("player", "The player to purge", MANDATORY),
            arg("options", "'force' to run without checking if player is registered", OPTIONAL));

        register(parent, BackupCommand.class, labels("backup"), "Perform a backup",
            "Creates a backup of the registered users.", AdminPermission.BACKUP);

        register(parent, PurgeLastPositionCommand.class,
            labels("resetpos", "purgelastposition", "purgelastpos", "resetposition",
                "resetlastposition", "resetlastpos"), "Purge player's last position",
            "Purge the last know position of the specified player or all of them.",
            AdminPermission.PURGE_LAST_POSITION, arg("player/*", "Player name or * for all players", MANDATORY));

        register(parent, PurgeBannedPlayersCommand.class,
            labels("purgebannedplayers", "purgebannedplayer", "deletebannedplayers", "deletebannedplayer"),
            "Purge banned players data", "Purge all AuthMeRemastered data for banned players.",
            AdminPermission.PURGE_BANNED_PLAYERS);
    }

    /** Registers the remaining general-purpose /authme subcommands. */
    private void registerUtilityCommands(CommandDescription parent) {
        register(parent, SwitchAntiBotCommand.class, labels("switchantibot", "toggleantibot", "antibot"),
            "Switch AntiBot mode", "Switch or toggle the AntiBot mode to the specified state.",
            AdminPermission.SWITCH_ANTIBOT, arg("mode", "ON / OFF", OPTIONAL));

        register(parent, ReloadCommand.class, labels("reload", "rld"), "Reload plugin",
            "Reload the AuthMeRemastered plugin.", AdminPermission.RELOAD);

        register(parent, VersionCommand.class, labels("version", "ver", "v", "about", "info"), "Version info",
            "Show detailed information about the installed AuthMeRemastered version, the developers, "
                + "contributors, and license.", null);

        register(parent, ConverterCommand.class, labels("converter", "convert", "conv"), "Converter command",
            "Converter command for AuthMeReloaded.", AdminPermission.CONVERTER,
            arg("job", "Conversion job: authplus / sqliteToSql / mysqlToSqlite", OPTIONAL));

        register(parent, UpdateHelpMessagesCommand.class, labels("messages", "msg"), "Add missing help messages",
            "Adds missing texts to the current help messages file.", AdminPermission.UPDATE_MESSAGES);

        register(parent, RecentPlayersCommand.class, labels("recent"),
            "See players who have recently logged in", "Shows the last players that have logged in.",
            AdminPermission.SEE_RECENT_PLAYERS);
    }

    /** Registers the /authme subcommands for setting premium mode on a player. */
    private void registerPremiumCommands(CommandDescription parent) {
        register(parent, SetPremiumAdminCommand.class, labels("premium", "setpremium"),
            "Enable premium for a player", "Enables premium mode for the specified player.",
            AdminPermission.SET_PREMIUM, arg("player", "Player name", MANDATORY));

        register(parent, SetFreemiumAdminCommand.class, labels("freemium", "setfreemium"),
            "Disable premium for a player", "Disables premium mode for the specified player.",
            AdminPermission.SET_FREEMIUM, arg("player", "Player name", MANDATORY));
    }

    /** Registers the /authme debug subcommand. */
    private void registerDebugCommand(CommandDescription parent) {
        register(parent, DebugCommand.class, labels("debug", "dbg"), "Debug features",
            "Allows various operations for debugging.", DebugSectionPermissions.DEBUG_COMMAND,
            arg("child", "The child to execute", OPTIONAL),
            arg("arg", "argument (depends on debug section)", OPTIONAL),
            arg("arg", "argument (depends on debug section)", OPTIONAL));
    }

    /**
     * Creates a command description for {@code /email} including its children.
     *
     * @return the email base command description
     */
    private CommandDescription buildEmailBaseCommand() {
        CommandDescription emailBase = register(null, EmailBaseCommand.class, labels("email"),
            "Add email or recover password", "The AuthMeRemastered email command base.", null);

        register(emailBase, ShowEmailCommand.class, labels("show", "myemail"), "Show Email",
            "Show your current email address.", PlayerPermission.SEE_EMAIL);

        register(emailBase, AddEmailCommand.class, labels("add", "addemail", "addmail"), "Add Email",
            "Add a new email address to your account.", PlayerPermission.ADD_EMAIL,
            arg("email", "Email address", MANDATORY), arg("verifyEmail", "Email address verification", MANDATORY));

        register(emailBase, ChangeEmailCommand.class, labels("change", "changeemail", "changemail"),
            "Change Email", "Change an email address of your account.", PlayerPermission.CHANGE_EMAIL,
            arg("oldEmail", "Old email address", MANDATORY), arg("newEmail", "New email address", MANDATORY));

        register(emailBase, RecoverEmailCommand.class,
            labels("recover", "recovery", "recoveremail", "recovermail"), "Recover password using email",
            "Recover your account using an Email address by sending a mail containing a new password.",
            PlayerPermission.RECOVER_EMAIL, arg("email", "Email address", MANDATORY));

        register(emailBase, ProcessCodeCommand.class, labels("code"), "Submit code to recover password",
            "Recover your account by submitting a code delivered to your email.",
            PlayerPermission.RECOVER_EMAIL, arg("code", "Recovery code", MANDATORY));

        register(emailBase, EmailSetPasswordCommand.class, labels("setpassword"),
            "Set new password after recovery", "Set a new password after successfully recovering your account.",
            PlayerPermission.RECOVER_EMAIL, arg("password", "New password", MANDATORY));

        register(emailBase, ConfirmEmailCommand.class, labels("confirm", "confirmemail"),
            "Confirm new email address", "Confirm a new email address by submitting the code sent to that address.",
            PlayerPermission.CONFIRM_EMAIL, arg("code", "Confirmation code", MANDATORY));

        return emailBase;
    }

    /**
     * Creates a command description object for {@code /totp} including its children.
     *
     * @return the totp base command description
     */
    private CommandDescription buildTotpBaseCommand() {
        CommandDescription totpBase = register(null, TotpBaseCommand.class, labels("totp", "2fa"), "TOTP commands",
            "Performs actions related to two-factor authentication.", null);

        register(totpBase, TotpCodeCommand.class, labels("code", "c"), "Command for logging in",
            "Processes the two-factor authentication code during login.", null,
            arg("code", "The TOTP code to use to log in", MANDATORY));

        register(totpBase, AddTotpCommand.class, labels("add"), "Enables TOTP",
            "Enables two-factor authentication for your account.", PlayerPermission.ENABLE_TWO_FACTOR_AUTH);

        register(totpBase, ConfirmTotpCommand.class, labels("confirm"), "Enables TOTP after successful code",
            "Saves the generated TOTP secret after confirmation.", PlayerPermission.ENABLE_TWO_FACTOR_AUTH,
            arg("code", "Code from the given secret from /totp add", MANDATORY));

        register(totpBase, RemoveTotpCommand.class, labels("remove"), "Removes TOTP",
            "Disables two-factor authentication for your account.", PlayerPermission.DISABLE_TWO_FACTOR_AUTH,
            arg("code", "Current 2FA code", MANDATORY));

        return totpBase;
    }

    /**
     * Sets the help command on all base commands, e.g. to register /authme help or /register help.
     *
     * @param commands the list of base commands to register a help child command on
     */
    private void setHelpOnAllBases(Collection<CommandDescription> commands) {
        for (CommandDescription base : commands) {
            register(base, HelpCommand.class, labels("help", "hlp", "h", "sos", "?"), "View help",
                "View detailed help for /" + base.getLabels().get(0) + " commands.", null,
                arg("query", "The command or query to view help for.", OPTIONAL));
        }
    }

    /**
     * Builds and registers a command, optionally with arguments and a permission.
     *
     * @param parent the parent command, or {@code null} for a base command
     * @param command the executable command class
     * @param labels the command labels
     * @param description the short description
     * @param detailedDescription the detailed description
     * @param permission the required permission, or {@code null} if none
     * @param arguments the command arguments
     * @return the registered command description
     */
    private static CommandDescription register(CommandDescription parent,
                                               Class<? extends ExecutableCommand> command,
                                               String[] labels, String description, String detailedDescription,
                                               PermissionNode permission, CommandArgumentDescription... arguments) {
        CommandDescription.CommandBuilder builder = CommandDescription.builder()
            .parent(parent)
            .labels(labels)
            .description(description)
            .detailedDescription(detailedDescription)
            .permission(permission)
            .executableCommand(command);
        for (CommandArgumentDescription argument : arguments) {
            builder.withArgument(argument.getName(), argument.getDescription(), argument.isOptional());
        }
        return builder.register();
    }

    // Creates a command argument description.
    private static CommandArgumentDescription arg(String name, String description, boolean optional) {
        return new CommandArgumentDescription(name, description, optional);
    }

    // Returns the given labels as an array.
    private static String[] labels(String... labels) {
        return labels;
    }
}
