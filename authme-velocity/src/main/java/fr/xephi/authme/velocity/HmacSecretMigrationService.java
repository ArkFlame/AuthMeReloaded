package fr.xephi.authme.velocity;

import ch.jalu.configme.configurationdata.ConfigurationData;
import ch.jalu.configme.migration.PlainMigrationService;
import ch.jalu.configme.resource.PropertyReader;
import fr.xephi.authme.proxy.ProxyMessageSecurity;
import fr.xephi.authme.velocity.config.VelocityConfigProperties;

class HmacSecretMigrationService extends PlainMigrationService {

    @Override
    protected boolean performMigrations(PropertyReader reader, ConfigurationData configurationData) {
        if (configurationData.getValue(VelocityConfigProperties.PROXY_SHARED_SECRET).isEmpty()) {
            configurationData.setValue(VelocityConfigProperties.PROXY_SHARED_SECRET, ProxyMessageSecurity.generateSecret());
            return MIGRATION_REQUIRED;
        }
        return NO_MIGRATION_NEEDED;
    }
}
