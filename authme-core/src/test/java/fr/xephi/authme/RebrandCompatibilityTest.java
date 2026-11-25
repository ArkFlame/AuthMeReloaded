package fr.xephi.authme;

import fr.xephi.authme.permission.AdminPermission;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * The AuthMeRemastered rebrand is display-only: plugin id/name, main class, package, config paths and
 * permission nodes must stay exactly as they were so existing installs keep working.
 */
class RebrandCompatibilityTest {

    @Test
    void shouldShowRemasteredBrandAndArkFlameAuthor() {
        assertThat(AuthMe.getPluginName(), equalTo("AuthMeRemastered"));
        assertThat(AuthMe.getPluginAuthor(), equalTo("ArkFlame Studios"));
    }

    @Test
    void shouldKeepPluginIdMainClassAndPackage() throws IOException {
        String rootPom = new String(Files.readAllBytes(findRootPom()), StandardCharsets.UTF_8);
        // plugin.yml "name" is generated from project.outputName - must remain "AuthMe"
        assertThat(rootPom, containsString("<project.outputName>AuthMe</project.outputName>"));
        assertThat(rootPom, containsString("<pluginDescription.main>fr.xephi.authme.AuthMe</pluginDescription.main>"));
        assertThat(rootPom, containsString("<pluginDescription.authors>ArkFlame Studios,"));
        assertThat(AuthMe.class.getName(), equalTo("fr.xephi.authme.AuthMe"));
    }

    @Test
    void shouldKeepConfigPathsAndPermissions() {
        assertThat(RegistrationSettings.REGISTER_MESSAGE_DELAY.getPath(),
            equalTo("settings.registration.registerMessageDelay"));
        assertThat(RegistrationSettings.LOGIN_MESSAGE_DELAY.getPath(),
            equalTo("settings.registration.loginMessageDelay"));
        assertThat(AdminPermission.REGISTER.getNode(), equalTo("authme.admin.register"));
    }

    private static Path findRootPom() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("authme-api/pom.xml"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Root pom not found");
        }
        return dir.resolve("pom.xml");
    }
}
