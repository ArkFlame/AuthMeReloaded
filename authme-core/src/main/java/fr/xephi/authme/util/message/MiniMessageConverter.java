package fr.xephi.authme.util.message;

import fr.xephi.authme.util.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

/**
 * Converts MiniMessage-formatted strings to legacy color codes so that the existing
 * Bukkit {@code ChatColor} send path keeps working on legacy servers.
 *
 * <p>The Adventure classes are optional: they are loaded from the {@code libraries}
 * section of plugin.yml and are absent on legacy (e.g. Spigot 1.8) servers. Every
 * reference to Adventure is isolated in a nested holder which is only touched after
 * an explicit class-availability check, so loading this class never requires
 * Adventure to be present.</p>
 */
public final class MiniMessageConverter {

    // Known MiniMessage tag names. Using a whitelist avoids treating message
    // placeholders such as <password> or <code> as MiniMessage tags.
    private static final String TAG_NAMES =
        "black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|"
        + "light_purple|yellow|white|bold|italic|underlined|strikethrough|obfuscated|reset|color|colour|"
        + "click|hover|keybind|key|translatable|lang|insert|rainbow|gradient|font|newline|pride";

    private static final Pattern MINIMESSAGE_TAG = Pattern.compile(
        "</?(?:" + TAG_NAMES + "|#[0-9a-fA-F]{6})(?::[^>]*)?>");

    private static final boolean ADVENTURE_AVAILABLE = detectAdventure();

    private MiniMessageConverter() {
    }

    /**
     * Returns whether the optional Adventure library is available on the classpath.
     *
     * @return true if MiniMessage can be used
     */
    public static boolean isAvailable() {
        return ADVENTURE_AVAILABLE;
    }

    /**
     * Checks whether the given text contains a MiniMessage tag.
     *
     * @param message the text to inspect (may be null)
     * @return true if a MiniMessage tag was found
     */
    public static boolean containsMiniMessageTags(String message) {
        return message != null && MINIMESSAGE_TAG.matcher(message).find();
    }

    /**
     * Converts {@code message} from MiniMessage into a legacy section-sign string when
     * conversion is enabled, Adventure is available and a MiniMessage tag is present.
     * In every other case the original string is returned unchanged, preserving the
     * legacy {@code '&'} color-code handling.
     *
     * @param message the message to convert (may be null)
     * @param enabled whether MiniMessage support is enabled in the settings
     * @return the converted message, or the original message when conversion does not apply
     */
    public static String convert(String message, boolean enabled) {
        if (message == null || !enabled || !ADVENTURE_AVAILABLE || !containsMiniMessageTags(message)) {
            return message;
        }
        try {
            return AdventureConverter.convert(message);
        } catch (Throwable t) {
            // Never let an optional library or a malformed tag break message delivery.
            return message;
        }
    }

    private static boolean detectAdventure() {
        try {
            return Utils.isClassLoaded("net.kyori.adventure.text.Component")
                && Utils.isClassLoaded("net.kyori.adventure.text.minimessage.MiniMessage")
                && Utils.isClassLoaded("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        } catch (LinkageError e) {
            return false;
        }
    }

    /** Holder whose class is only loaded once Adventure is known to be available. */
    private static final class AdventureConverter {
        private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
        private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

        private AdventureConverter() {
        }

        static String convert(String message) {
            Component component = MINI_MESSAGE.deserialize(message);
            return SERIALIZER.serialize(component);
        }
    }
}
