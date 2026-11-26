package fr.xephi.authme.platform;

/**
 * Platform adapter for Spigot 1.8.8 (legacy versions).
 */
public class SpigotPlatformAdapter extends AbstractSpigotPlatformAdapter {

    private static final SpigotCompatibility COMPATIBILITY = new SpigotCompatibility(
        "This AuthMe Spigot 1.8 build requires the Bukkit/Spigot 1.8+ API.",
        "org.spigotmc.event.player.PlayerInteractAtEntityEvent");

    @Override
    public String getPlatformName() {
        return "spigot-1.8";
    }

    @Override
    public String getCompatibilityError() {
        return COMPATIBILITY.getError();
    }
}
