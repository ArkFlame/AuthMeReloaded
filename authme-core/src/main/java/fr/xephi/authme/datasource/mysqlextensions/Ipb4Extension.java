package fr.xephi.authme.datasource.mysqlextensions;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.Columns;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Extension for IPB4.
 */
class Ipb4Extension extends MySqlExtension {

    private final String ipbPrefix;
    private final int ipbGroup;

    Ipb4Extension(Settings settings, Columns col) {
        super(settings, col);
        this.ipbPrefix = settings.getProperty(HooksSettings.IPB_TABLE_PREFIX);
        this.ipbGroup = settings.getProperty(HooksSettings.IPB_ACTIVATED_GROUP_ID);
    }

    @Override
    public void saveAuth(PlayerAuth auth, Connection con) throws SQLException {
        String fullTableName = ipbPrefix + tableName;
        String name = auth.getNickname();
        // Update player group in core_members
        setIntColumns(fullTableName, tableName, new String[]{"member_group_id"}, ipbGroup, name, con);
        // Get current time without ms, then update joined date and last visit
        long time = System.currentTimeMillis() / 1000;
        setLongColumns(fullTableName, tableName, new String[]{"joined", "last_visit"}, time, name, con);
    }
}
