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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskAsynchronously;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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
        String name1 = PLAYER;
        Player player1 = mockPlayerWithName(name1);
        given(dataSource.getEmail(name1)).willReturn(DataSourceValueImpl.of(EMAIL));
        given(permissionsManager.hasPermission(player1, PlayerPermission.VERIFICATION_CODE)).willReturn(true);
        String name2 = "StillLovingTests";
        Player player2 = mockPlayerWithName(name2);

        VerificationCodeManager codeManager = createCodeManager();
        codeManager.verify(name2);

        // when
        boolean test1 = codeManager.isVerificationRequired(player1);
        boolean test2 = codeManager.isVerificationRequired(player2);

        // then
        assertThat(test1, equalTo(true));
        assertThat(test2, equalTo(false));
        verify(dataSource, only()).getEmail(name1);
        verify(permissionsManager, only()).hasPermission(player1, PlayerPermission.VERIFICATION_CODE);
    }

    @Test
    void shouldNotRequireVerificationIfEmailSettingsAreIncomplete() {
        // given
        given(emailService.hasAllInformation()).willReturn(false);
        VerificationCodeManager codeManager = createCodeManager();
        Player player = mock(Player.class);

        // when
        boolean result = codeManager.isVerificationRequired(player);

        // then
        assertThat(result, equalTo(false));
        verifyNoInteractions(permissionsManager, dataSource);
    }

    @Test
    void shouldNotRequireVerificationForMissingPermission() {
        // given
        given(emailService.hasAllInformation()).willReturn(true);
        Player player = mockPlayerWithName(PLAYER);
        given(permissionsManager.hasPermission(player, PlayerPermission.VERIFICATION_CODE)).willReturn(false);
        VerificationCodeManager codeManager = createCodeManager();

        // when
        boolean result = codeManager.isVerificationRequired(player);

        // then
        assertThat(result, equalTo(false));
        verify(permissionsManager).hasPermission(player, PlayerPermission.VERIFICATION_CODE);
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldGenerateAndRequireCode() {
        // given
        given(emailService.hasAllInformation()).willReturn(true);
        String player = PLAYER;
        String email = EMAIL;
        given(dataSource.getEmail(player)).willReturn(DataSourceValueImpl.of(email));
        given(emailService.sendVerificationMail(eq(player), eq(email), anyString())).willReturn(true);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        VerificationCodeManager codeManager1 = createCodeManager();
        VerificationCodeManager codeManager2 = createCodeManager();
        codeManager2.codeExistOrGenerateNew(player);

        // when
        boolean hasCode1 = codeManager1.hasCode(player);
        boolean hasCode2 = codeManager2.hasCode(player);
        boolean codeRequired1 = codeManager1.isCodeRequired(player);
        boolean codeRequired2 = codeManager2.isCodeRequired(player);

        // then
        assertThat(hasCode1, equalTo(false));
        assertThat(hasCode2, equalTo(true));
        assertThat(codeRequired1, equalTo(false));
        assertThat(codeRequired2, equalTo(true));
    }

    @Test
    void shouldVerifyCode() {
        // given
        given(emailService.hasAllInformation()).willReturn(true);
        String player = PLAYER;
        String code = "193458";
        String email = EMAIL;
        given(dataSource.getEmail(player)).willReturn(DataSourceValueImpl.of(email));
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        VerificationCodeManager codeManager1 = createCodeManager();
        VerificationCodeManager codeManager2 = createCodeManager();
        codeManager1.codeExistOrGenerateNew(player);

        // when
        boolean test1 = codeManager1.checkCode(player, code);
        boolean test2 = codeManager2.checkCode(player, code);

        // then
        assertThat(test1, equalTo(false));
        assertThat(test2, equalTo(false));
    }

    @Test
    void shouldLookupEmailOnlyInAsyncTask() {
        // given
        String player = PLAYER;
        String email = EMAIL;
        VerificationCodeManager codeManager = createCodeManager();

        // when
        codeManager.codeExistOrGenerateNew(player);

        // then
        verify(bukkitService).runTaskAsynchronously(runnableCaptor.capture());
        verifyNoInteractions(dataSource);
        verify(emailService, only()).hasAllInformation();

        given(dataSource.getEmail(player)).willReturn(DataSourceValueImpl.of(email));
        given(emailService.sendVerificationMail(eq(player), eq(email), anyString())).willReturn(true);
        runnableCaptor.getValue().run();

        assertThat(codeManager.hasCode(player), equalTo(true));
        verify(dataSource).getEmail(player);
        verify(emailService).sendVerificationMail(eq(player), eq(email), anyString());
    }

    @Test
    void shouldNotStoreCodeIfEmailSendFails() {
        // given
        String player = PLAYER;
        String email = EMAIL;
        given(dataSource.getEmail(player)).willReturn(DataSourceValueImpl.of(email));
        given(emailService.sendVerificationMail(eq(player), eq(email), anyString())).willReturn(false);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        VerificationCodeManager codeManager = createCodeManager();

        // when
        codeManager.codeExistOrGenerateNew(player);

        // then
        assertThat(codeManager.hasCode(player), equalTo(false));
    }

    @Test
    void shouldNotScheduleDuplicateCodeGenerationWhilePending() {
        // given
        String player = PLAYER;
        VerificationCodeManager codeManager = createCodeManager();

        // when
        codeManager.codeExistOrGenerateNew(player);
        codeManager.codeExistOrGenerateNew(player);

        // then
        verify(bukkitService).runTaskAsynchronously(any(Runnable.class));
        verifyNoInteractions(dataSource);
        verify(emailService, only()).hasAllInformation();
    }

    private VerificationCodeManager createCodeManager() {
        return new VerificationCodeManager(settings, dataSource, emailService, permissionsManager, bukkitService);
    }
}
