package fr.xephi.authme.command.help;

/**
 * Common, non-generic keys for messages used when showing command help.
 * All keys are prefixed with {@code common}.
 */
public enum HelpMessage {

    HEADER("header"),

    OPTIONAL("optional"),

    HAS_PERMISSION("hasPermission"),

    NO_PERMISSION("noPermission"),

    DEFAULT("default"),

    RESULT("result");

    private static final String PREFIX = "common.";
    private final LocalizedKey key;

    /**
     * Constructor.
     *
     * @param key the message key without the common prefix
     */
    HelpMessage(String key) {
        this.key = new LocalizedKey(PREFIX, key);
    }

    /** @return the message key */
    public String getKey() {
        return key.getKey();
    }

    /** @return the key without the common prefix */
    public String getEntryKey() {
        return key.getEntryKey();
    }
}
