package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.TestHelper;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for converter tests that need the logger to be initialized.
 */
abstract class AbstractConverterTest {

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }
}
