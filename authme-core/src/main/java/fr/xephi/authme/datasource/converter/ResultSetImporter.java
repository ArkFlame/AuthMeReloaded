package fr.xephi.authme.datasource.converter;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Callback that consumes the rows returned by a converter query.
 */
@FunctionalInterface
interface ResultSetImporter {

    void importRows(ResultSet rs) throws SQLException;
}
