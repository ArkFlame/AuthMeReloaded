package fr.xephi.authme.command.help;

/**
 * Translatable sections. Message keys are prefixed by {@code section}.
 */
public enum HelpSection implements HelpTextKey {

    COMMAND("command"),

    SHORT_DESCRIPTION("description"),

    DETAILED_DESCRIPTION("detailedDescription"),

    ARGUMENTS("arguments"),

    ALTERNATIVES("alternatives"),

    PERMISSIONS("permissions"),

    CHILDREN("children");

    private final String entryKey;

    /**
     * Constructor.
     *
     * @param entryKey the message key without the section prefix
     */
    HelpSection(String entryKey) {
        this.entryKey = entryKey;
    }

    @Override
    public String getPrefix() {
        return "section.";
    }

    @Override
    public String getEntryKey() {
        return entryKey;
    }
}
