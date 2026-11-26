package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.service.BukkitService;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class VersionCommand implements ExecutableCommand {

    @Inject
    private BukkitService bukkitService;

    @Override
    public void executeCommand(CommandSender sender, List<String> arguments) {
        sender.sendMessage(ChatColor.GOLD + "==========[ " + AuthMe.getPluginName() + " ABOUT ]==========");
        sender.sendMessage(ChatColor.GOLD + "Version: " + ChatColor.WHITE + AuthMe.getPluginName()
            + " v" + AuthMe.getPluginVersion() + ChatColor.GRAY + " (build: " + AuthMe.getPluginBuildNumber() + ")");
        sender.sendMessage(ChatColor.GOLD + "Maintained by: " + ChatColor.WHITE + AuthMe.getPluginAuthor());
        sender.sendMessage(ChatColor.GOLD + "Original authors:");
        Collection<Player> onlinePlayers = bukkitService.getOnlinePlayers();
        printDeveloper(sender, "Gabriele C.", "sgdc3", "Project manager, Contributor", onlinePlayers);
        printDeveloper(sender, "Lucas J.", "ljacqu", "Main Developer", onlinePlayers);
        printDeveloper(sender, "games647", "games647", "Developer", onlinePlayers);
        printDeveloper(sender, "Hex3l", "Hex3l", "Developer", onlinePlayers);
        printDeveloper(sender, "krusic22", "krusic22", "Support", onlinePlayers);
        sender.sendMessage(ChatColor.GOLD + "Retired authors:");
        printDeveloper(sender, "Alexandre Vanhecke", "xephi59", "Original Author", onlinePlayers);
        printDeveloper(sender, "Gnat008", "gnat008", "Developer, Retired", onlinePlayers);
        printDeveloper(sender, "DNx5", "DNx5", "Developer, Retired", onlinePlayers);
        printDeveloper(sender, "Tim Visee", "timvisee", "Developer, Retired", onlinePlayers);
        sender.sendMessage(ChatColor.GOLD + "Website: " + ChatColor.WHITE + "https://github.com/ArkFlame/AuthMeReloaded");
        sender.sendMessage(ChatColor.GOLD + "License: " + ChatColor.WHITE + "GNU GPL v3.0"
            + ChatColor.GRAY + ChatColor.ITALIC + " (See LICENSE file)");
        sender.sendMessage(ChatColor.GOLD + "Copyright: " + ChatColor.WHITE + "Copyright (c) AuthMe-Team "
            + new SimpleDateFormat("yyyy").format(new Date()) + ". Released under GPL v3 License.");
    }

    private static void printDeveloper(CommandSender sender, String name, String minecraftName, String function,
                                       Collection<Player> onlinePlayers) {
        String msg = " " + ChatColor.WHITE + name + ChatColor.GRAY + " // " + ChatColor.WHITE + minecraftName
            + ChatColor.GRAY + ChatColor.ITALIC + " (" + function + ")"
            + (onlinePlayers.stream().anyMatch(player -> player.getName().equalsIgnoreCase(minecraftName))
                ? ChatColor.GREEN.toString() + ChatColor.ITALIC + " (In-Game)" : "");
        sender.sendMessage(msg);
    }
}
