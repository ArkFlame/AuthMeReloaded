package fr.xephi.authme.command.help;

/**
 * Common, non-generic keys for messages used when showing command help.
 * All keys are prefixed with {@code common}.
 */
public enum HelpMessage implements HelpTextKey {

    HEADER("header"),

    OPTIONAL("optional"),

    HAS_PERMISSION("hasPermission"),

    NO_PERMISSION("noPermission"),

    DEFAULT("default"),

    RESULT("result");

    private static final String PREFIX = "common.";
    private final String entryKey;

    /**
     * Constructor.
     *
     * @param entryKey the message key without the common prefix
     */
    HelpMessage(String entryKey) {
        this.entryKey = entryKey;
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public String getEntryKey() {
        return entryKey;
    }
}
