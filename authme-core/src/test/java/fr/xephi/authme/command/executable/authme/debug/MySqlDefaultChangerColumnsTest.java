package fr.xephi.authme.command.executable.authme.debug;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Consistency test for {@link MySqlDefaultChanger.Columns} enum.
 */
class MySqlDefaultChangerColumnsTest {

    @Test
    void shouldAllHaveDifferentNameProperty() {
        Set<String> properties = new HashSet<>();
        for (MySqlDefaultChanger.Columns col : MySqlDefaultChanger.Columns.values()) {
            if (!properties.add(col.getColumnNameProperty().getPath())) {
                fail("Column '" + col + "' has a column name property path that was already encountered: "
                    + col.getColumnNameProperty().getPath());
            }
        }
    }

    @Test
    void shouldHaveMatchingColumnDefinitions() {
        for (MySqlDefaultChanger.Columns col : MySqlDefaultChanger.Columns.values()) {
            verifyColumnDefinitions(col);
        }
    }

    private void verifyColumnDefinitions(MySqlDefaultChanger.Columns column) {
        String nullable = column.getNullableDefinition();
        String notNull = column.getNotNullDefinition();

        String expectedNotNull = nullable + " NOT NULL DEFAULT ";
        assertThat(column.name(), notNull.startsWith(expectedNotNull), equalTo(true));
        // Check that `notNull` length is bigger because we expect a value after DEFAULT
        assertThat(column.name(), notNull.length() > expectedNotNull.length(), equalTo(true));

        Object defaultValue = column.getDefaultValue();
        String defaultValueAsString = String.valueOf(defaultValue);
        if (!notNull.endsWith("DEFAULT " + defaultValueAsString)
                && !notNull.endsWith("DEFAULT '" + defaultValueAsString + "'")) {
            fail("Expected '" + column + "' not-null definition to contain DEFAULT " + defaultValueAsString);
        }
    }
}
