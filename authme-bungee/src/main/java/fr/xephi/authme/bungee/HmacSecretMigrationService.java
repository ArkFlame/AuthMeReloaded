package fr.xephi.authme.bungee;

import ch.jalu.configme.configurationdata.ConfigurationData;
import ch.jalu.configme.migration.PlainMigrationService;
import ch.jalu.configme.resource.PropertyReader;
import fr.xephi.authme.bungee.config.BungeeConfigProperties;

class HmacSecretMigrationService extends PlainMigrationService {

    @Override
    protected boolean performMigrations(PropertyReader reader, ConfigurationData configurationData) {
        if (configurationData.getValue(BungeeConfigProperties.PROXY_SHARED_SECRET).isEmpty()) {
            configurationData.setValue(BungeeConfigProperties.PROXY_SHARED_SECRET, ProxyMessageSecurity.generateSecret());
            return MIGRATION_REQUIRED;
        }
        return NO_MIGRATION_NEEDED;
    }
}
