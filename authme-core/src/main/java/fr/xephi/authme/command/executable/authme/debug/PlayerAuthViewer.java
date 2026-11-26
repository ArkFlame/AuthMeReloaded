package fr.xephi.authme.command.executable.authme.debug;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.permission.DebugSectionPermissions;
import fr.xephi.authme.permission.PermissionNode;
import fr.xephi.authme.security.crypts.HashedPassword;
import fr.xephi.authme.util.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static fr.xephi.authme.command.executable.authme.debug.DebugSectionUtils.formatLocation;

/**
 * Allows to view the data of a PlayerAuth in the database.
 */
class PlayerAuthViewer implements DebugSection {

    @Inject
    private DataSource dataSource;

    @Override
    public String getName() {
        return "db";
    }

    @Override
    public String getDescription() {
        return "View player's data in the database";
    }

    @Override
    public void execute(CommandSender sender, List<String> arguments) {
        if (arguments.isEmpty()) {
            sendTitle(sender, "database viewer");
            sender.sendMessage("Enter player name to view his data in the database.");
            sender.sendMessage("Example: /authme debug db Bobby");
            return;
        }

        String name = arguments.get(0);
        PlayerAuth auth = dataSource.getAuth(name);
        if (auth == null) {
            sendTitle(sender, "database viewer");
            sender.sendMessage("No record exists for '" + name + "'");
            return;
        }

        sender.sendMessage(ChatColor.BLUE + "[AuthMe] Player " + auth.getNickname() + " / " + auth.getRealName());
        sender.sendMessage("Email: " + auth.getEmail() + ". IP: " + auth.getLastIp() + ". Group: " + auth.getGroupId());
        sender.sendMessage("Quit location: "
            + formatLocation(auth.getQuitLocX(), auth.getQuitLocY(), auth.getQuitLocZ(), auth.getWorld()));
        sender.sendMessage("Last login: " + formatDate(auth.getLastLogin()));
        sender.sendMessage("Registration: " + formatDate(auth.getRegistrationDate())
            + " with IP " + auth.getRegistrationIp());

        HashedPassword hashedPass = auth.getPassword();
        sender.sendMessage("Hash / salt (partial): '" + safeSubstring(hashedPass.getHash(), 6)
            + "' / '" + safeSubstring(hashedPass.getSalt(), 4) + "'");
        sender.sendMessage("TOTP code (partial): '" + safeSubstring(auth.getTotpKey(), 3) + "'");
    }

    @Override
    public PermissionNode getRequiredPermission() {
        return DebugSectionPermissions.PLAYER_AUTH_VIEWER;
    }

    /**
     * Fail-safe substring method. Guarantees not to show the entire String.
     *
     * @param str the string to transform
     * @param length number of characters to show from the start of the String
     * @return the first <code>length</code> characters of the string, or half of the string if it is shorter,
     *         or empty string if the string is null or empty
     */
    private static String safeSubstring(String str, int length) {
        if (StringUtils.isBlank(str)) {
            return "";
        }
        int end = str.length() < length ? str.length() / 2 : length;
        return str.substring(0, end) + "...";
    }

    /**
     * Formats the given timestamp to a human readable date.
     *
     * @param timestamp the timestamp to format (nullable)
     * @return the formatted timestamp
     */
    private static String formatDate(Long timestamp) {
        if (timestamp == null || timestamp == 0) {
            return "Not available (" + (timestamp == null ? "null" : "0") + ")";
        }
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
    }
}
