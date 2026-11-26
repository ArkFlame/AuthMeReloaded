package fr.xephi.authme.command.help;

/**
 * Translatable sections. Message keys are prefixed by {@code section}.
 */
public enum HelpSection {

    COMMAND("command"),

    SHORT_DESCRIPTION("description"),

    DETAILED_DESCRIPTION("detailedDescription"),

    ARGUMENTS("arguments"),

    ALTERNATIVES("alternatives"),

    PERMISSIONS("permissions"),

    CHILDREN("children");

    private static final String PREFIX = "section.";
    private final LocalizedKey key;

    /**
     * Constructor.
     *
     * @param key the message key without the section prefix
     */
    HelpSection(String key) {
        this.key = new LocalizedKey(PREFIX, key);
    }

    /** @return the message key */
    public String getKey() {
        return key.getKey();
    }

    /** @return the key without the section prefix */
    public String getEntryKey() {
        return key.getEntryKey();
    }
}
