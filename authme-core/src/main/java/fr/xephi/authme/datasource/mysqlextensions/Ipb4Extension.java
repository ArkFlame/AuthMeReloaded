package fr.xephi.authme.datasource.mysqlextensions;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.Columns;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
        // Update player group in core_members
        String sql = "UPDATE " + ipbPrefix + tableName
                   + " SET " + tableName + ".member_group_id=? WHERE " + col.NAME + "=?;";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setInt(1, ipbGroup);
            pst.setString(2, auth.getNickname());
            pst.executeUpdate();
        }
        // Get current time without ms
        long time = System.currentTimeMillis() / 1000;
        // Update joined date and last visit
        for (String column : new String[]{"joined", "last_visit"}) {
            sql = "UPDATE " + ipbPrefix + tableName
                + " SET " + tableName + "." + column + "=? WHERE " + col.NAME + "=?;";
            try (PreparedStatement pst = con.prepareStatement(sql)) {
                pst.setLong(1, time);
                pst.setString(2, auth.getNickname());
                pst.executeUpdate();
            }
        }
    }
}
