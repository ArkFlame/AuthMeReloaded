package fr.xephi.authme.command.help;

/**
 * Common key handling for localized help text enums such as {@link HelpMessage} and {@link HelpSection}.
 * <p>
 * Implementations only supply their prefix and the unprefixed entry key; the full message key is derived
 * in one place instead of duplicating the prefix logic in every enum.
 */
public interface HelpTextKey {

    /** @return the prefix prepended to all keys of this type */
    String getPrefix();

    /** @return the message key without the prefix */
    String getEntryKey();

    /** @return the full message key, including the prefix */
    default String getKey() {
        return getPrefix() + getEntryKey();
    }
}
