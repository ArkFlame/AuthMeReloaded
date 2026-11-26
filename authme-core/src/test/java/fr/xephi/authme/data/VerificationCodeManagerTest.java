package fr.xephi.authme.data;

import ch.jalu.datasourcecolumns.data.DataSourceValueImpl;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.permission.PlayerPermission;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.SecuritySettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskAsynchronously;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link VerificationCodeManager}.
 */
@ExtendWith(MockitoExtension.class)
class VerificationCodeManagerTest {

    private static final String PLAYER = "ILoveTests";
    private static final String EMAIL = "ilovetests@test.com";

    @Mock
    private Settings settings;

    @Mock
    private DataSource dataSource;

    @Mock
    private EmailService emailService;

    @Mock
    private PermissionsManager permissionsManager;

    @Mock
    private BukkitService bukkitService;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;

    @BeforeEach
    void setUpBasicBehavior() {
        given(settings.getProperty(SecuritySettings.VERIFICATION_CODE_EXPIRATION_MINUTES)).willReturn(1);
    }

    @Test
    void shouldRequireVerification() {
        // given
        given(emailService.hasAllInformation()).willReturn(true);
        Player player1 = mockPlayerWithName(PLAYER);
        given(dataSource.getEmail(PLAYER)).willReturn(DataSourceValueImpl.of(EMAIL));
        given(permissionsManager.hasPermission(player1, PlayerPermission.VERIFICATION_CODE)).willReturn(true);
        Player player2 = mockPlayerWithName("StillLovingTests");

        VerificationCodeManager codeManager = createCodeManager();
        codeManager.verify("StillLovingTests");

        // when
        boolean test1 = codeManager.isVerificationRequired(player1);
        boolean test2 = codeManager.isVerificationRequired(player2);

        // then
        assertThat(test1, equalTo(true));
        assertThat(test2, equalTo(false));
        verify(dataSource, only()).getEmail(PLAYER);
        verify(permissionsManager, only()).hasPermission(player1, PlayerPermission.VERIFICATION_CODE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldNotRequireVerificationWhenCheckFails(boolean emailSettingsComplete) {
        // given
        given(emailService.hasAllInformation()).willReturn(emailSettingsComplete);
        Player player = mockPlayerWithName(PLAYER);
        if (emailSettingsComplete) {
            given(permissionsManager.hasPermission(player, PlayerPermission.VERIFICATION_CODE)).willReturn(false);
        }
        VerificationCodeManager codeManager = createCodeManager();

        // when
        boolean result = codeManager.isVerificationRequired(player);

        // then
        assertThat(result, equalTo(false));
        if (emailSettingsComplete) {
            verify(permissionsManager).hasPermission(player, PlayerPermission.VERIFICATION_CODE);
            verifyNoInteractions(dataSource);
        } else {
            verifyNoInteractions(permissionsManager, dataSource);
        }
    }

    @Test
    void shouldGenerateAndRequireCode() {
        // given
        given(emailService.hasAllInformation()).willReturn(true);
        given(dataSource.getEmail(PLAYER)).willReturn(DataSourceValueImpl.of(EMAIL));
        given(emailService.sendVerificationMail(eq(PLAYER), eq(EMAIL), anyString())).willReturn(true);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        VerificationCodeManager codeManager1 = createCodeManager();
        VerificationCodeManager codeManager2 = createCodeManager();
        codeManager2.codeExistOrGenerateNew(PLAYER);

        // then
        assertThat(codeManager1.hasCode(PLAYER), equalTo(false));
        assertThat(codeManager2.hasCode(PLAYER), equalTo(true));
        assertThat(codeManager1.isCodeRequired(PLAYER), equalTo(false));
        assertThat(codeManager2.isCodeRequired(PLAYER), equalTo(true));
    }

    @Test
    void shouldLookupEmailOnlyInAsyncTask() {
        // given
        VerificationCodeManager codeManager = createCodeManager();

        // when
        codeManager.codeExistOrGenerateNew(PLAYER);
        codeManager.codeExistOrGenerateNew(PLAYER);

        // then
        verify(bukkitService).runTaskAsynchronously(runnableCaptor.capture());
        verifyNoInteractions(dataSource);
        verify(emailService, only()).hasAllInformation();

        given(dataSource.getEmail(PLAYER)).willReturn(DataSourceValueImpl.of(EMAIL));
        given(emailService.sendVerificationMail(eq(PLAYER), eq(EMAIL), anyString())).willReturn(true);
        runnableCaptor.getValue().run();

        assertThat(codeManager.hasCode(PLAYER), equalTo(true));
        verify(dataSource).getEmail(PLAYER);
        verify(emailService).sendVerificationMail(eq(PLAYER), eq(EMAIL), anyString());
    }

    @Test
    void shouldNotStoreCodeIfEmailSendFails() {
        // given
        given(dataSource.getEmail(PLAYER)).willReturn(DataSourceValueImpl.of(EMAIL));
        given(emailService.sendVerificationMail(eq(PLAYER), eq(EMAIL), anyString())).willReturn(false);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        VerificationCodeManager codeManager = createCodeManager();

        // when
        codeManager.codeExistOrGenerateNew(PLAYER);

        // then
        assertThat(codeManager.hasCode(PLAYER), equalTo(false));
        assertThat(codeManager.checkCode(PLAYER, "193458"), equalTo(false));
    }

    private VerificationCodeManager createCodeManager() {
        return new VerificationCodeManager(settings, dataSource, emailService, permissionsManager, bukkitService);
    }
}
