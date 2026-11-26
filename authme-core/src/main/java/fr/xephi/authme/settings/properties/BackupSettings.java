package fr.xephi.authme.settings.properties;

import ch.jalu.configme.properties.Property;

/**
 * Source-compatible access point for the backup settings, which live in {@link PurgeSettings}.
 *
 * <p>The properties are declared by the single settings holder so they are registered with
 * ConfigMe exactly once. This class only forwards to those declarations.
 */
public final class BackupSettings {

    public static final Property<Boolean> ENABLED = PurgeSettings.ENABLED;
    public static final Property<Boolean> ON_SERVER_START = PurgeSettings.ON_SERVER_START;
    public static final Property<Boolean> ON_SERVER_STOP = PurgeSettings.ON_SERVER_STOP;
    public static final Property<String> MYSQL_WINDOWS_PATH = PurgeSettings.MYSQL_WINDOWS_PATH;

    private BackupSettings() {
    }
}
