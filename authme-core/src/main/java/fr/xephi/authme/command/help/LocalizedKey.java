package fr.xephi.authme.command.help;

/**
 * A prefixed key for a localized help text.
 * <p>
 * Keeps the full key together with its prefix so that the unprefixed entry key can be derived
 * without repeating the prefix in every enum that uses this scheme (see {@link HelpMessage}
 * and {@link HelpSection}).
 */
final class LocalizedKey {

    private final String prefix;
    private final String key;

    LocalizedKey(String prefix, String entryKey) {
        this.prefix = prefix;
        this.key = prefix + entryKey;
    }

    /** @return the full message key, including the prefix */
    String getKey() {
        return key;
    }

    /** @return the key without its prefix */
    String getEntryKey() {
        return key.substring(prefix.length());
    }
}
