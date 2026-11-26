package fr.xephi.authme.settings.properties;

import ch.jalu.configme.SettingsHolder;
import ch.jalu.configme.configurationdata.ConfigurationData;
import ch.jalu.configme.properties.Property;
import fr.xephi.authme.ClassCollector;
import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.TestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for {@link SettingsHolder} implementations.
 */
class SettingsClassConsistencyTest {

    private static final String SETTINGS_FOLDER = TestHelper.PROJECT_ROOT + "settings/properties";
    private static Map<Class<? extends SettingsHolder>, List<Field>> propertyFields;

    @BeforeAll
    static void scanForSettingsClasses() {
        ClassCollector collector = new ClassCollector(TestHelper.SOURCES_FOLDER, SETTINGS_FOLDER);
        List<Class<? extends SettingsHolder>> classes = collector.collectClasses(SettingsHolder.class);

        if (classes.isEmpty()) {
            throw new IllegalStateException("Did not find any SettingsHolder classes. Is the folder correct?");
        }
        propertyFields = new LinkedHashMap<>();
        for (Class<? extends SettingsHolder> clazz : classes) {
            propertyFields.put(clazz, getPropertyFields(clazz));
        }
    }

    /**
     * Make sure that all {@link Property} instances are defined as public, static, final fields
     * and that no two constants use the same path.
     */
    @Test
    void shouldHaveValidPropertyFields() {
        Set<String> paths = new HashSet<>();
        propertyFields.forEach((clazz, fields) -> {
            for (Field field : fields) {
                assertThat("Field " + clazz.getSimpleName() + "#" + field.getName()
                        + " should be public, static, and final",
                    isValidConstantField(field), equalTo(true));
                Property<?> property =
                    ReflectionTestUtils.getFieldValue(clazz, null, field.getName());
                if (!paths.add(property.getPath())) {
                    fail("Path '" + property.getPath() + "' should be used by only one constant");
                }
            }
        });
    }

    /**
     * Checks that {@link AuthMeSettingsRetriever} returns a ConfigurationData with all
     * available SettingsHolder classes.
     */
    @Test
    void shouldHaveAllClassesInConfigurationData() {
        // given
        long totalProperties = propertyFields.values().stream()
            .mapToLong(List::size)
            .sum();

        // when
        ConfigurationData configData = AuthMeSettingsRetriever.buildConfigurationData();

        // then
        assertThat("ConfigurationData should have " + totalProperties + " properties (as found manually)",
            configData.getProperties(), hasSize((int) totalProperties));
    }

    private static List<Field> getPropertyFields(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
            .filter(field -> Property.class.isAssignableFrom(field.getType()))
            .collect(Collectors.toList());
    }

    private static boolean isValidConstantField(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers);
    }
}
