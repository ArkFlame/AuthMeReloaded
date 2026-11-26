package fr.xephi.authme.settings.properties;

import ch.jalu.configme.properties.Property;

/**
 * Source-compatible access point for the premium settings, which live in {@link PluginSettings}.
 *
 * <p>The properties are declared by the single settings holder so they are registered with
 * ConfigMe exactly once. This class only forwards to those declarations.
 */
public final class PremiumSettings {

    public static final Property<Boolean> ENABLE_PREMIUM = PluginSettings.ENABLE_PREMIUM;
    public static final Property<Boolean> AUTO_REGISTER = PluginSettings.AUTO_REGISTER;

    private PremiumSettings() {
    }
}
