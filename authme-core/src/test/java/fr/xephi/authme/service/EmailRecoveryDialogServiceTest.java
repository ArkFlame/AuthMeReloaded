package fr.xephi.authme.service;

import ch.jalu.datasourcecolumns.data.DataSourceValueImpl;
import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.mail.EmailService;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.platform.DialogAdapter;
import fr.xephi.authme.platform.DialogWindowSpec;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskAsynchronously;
import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for {@link EmailRecoveryDialogService}.
 */
@ExtendWith(MockitoExtension.class)
class EmailRecoveryDialogServiceTest {

    private static final String NAME = "SomePlayer";
    private static final String EMAIL = "player@example.com";

    @InjectMocks
    private EmailRecoveryDialogService service;

    @Mock
    private CommonService commonService;

    @Mock
    private DialogAdapter dialogAdapter;

    @Mock
    private EmailService emailService;

    @Mock
    private DataSource dataSource;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private ValidationService validationService;

    @Mock
    private RecoveryCodeService recoveryCodeService;

    @Mock
    private PasswordRecoveryService passwordRecoveryService;

    @Mock
    private BukkitService bukkitService;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldSendRecoveryCodeAndOnlyOfferDialogWhenAllowed(boolean rateLimited) {
        // given
        Player player = mockPlayer();
        givenValidEmailInput();
        given(recoveryCodeService.isRecoveryCodeNeeded()).willReturn(true);
        given(passwordRecoveryService.isOnEmailCooldown(player)).willReturn(rateLimited);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);
        if (!rateLimited) {
            givenDialogFlowEnabled(true);
            given(dialogAdapter.isDialogSupported()).willReturn(true);
            givenDialogTextStubs(player);
            setBukkitServiceToScheduleSyncEntityTaskFromOptionallyAsyncTask(bukkitService);
        }

        // when
        service.requestRecovery(player, EMAIL);

        // then
        verify(passwordRecoveryService).createAndSendRecoveryCode(player, EMAIL);
        verify(passwordRecoveryService, never()).generateAndSendNewPassword(player, EMAIL);
        if (rateLimited) {
            verify(dialogAdapter, never()).showRecoveryCodeDialog(eq(player), any(DialogWindowSpec.class));
        } else {
            verify(dialogAdapter).showRecoveryCodeDialog(eq(player), any(DialogWindowSpec.class));
        }
    }

    @Test
    void shouldSendNewPasswordWhenCodesAreDisabled() {
        // given
        Player player = mockPlayer();
        givenValidEmailInput();
        given(recoveryCodeService.isRecoveryCodeNeeded()).willReturn(false);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        service.requestRecovery(player, EMAIL);

        // then
        verify(passwordRecoveryService).generateAndSendNewPassword(player, EMAIL);
        verifyNoInteractions(dialogAdapter);
    }

    @Test
    void shouldNotOfferPasswordDialogForExpiredOrInvalidCode() {
        // given
        Player player = mockPlayer();
        given(recoveryCodeService.hasTriesLeft(NAME)).willReturn(true);
        given(recoveryCodeService.isCodeValid(NAME, "expired")).willReturn(false);
        given(recoveryCodeService.getTriesLeft(NAME)).willReturn(1);

        // when
        service.submitCode(player, "expired");

        // then
        verify(commonService).send(player, MessageKey.INCORRECT_RECOVERY_CODE, "1");
        verifyNoInteractions(dialogAdapter);
        verify(passwordRecoveryService, never()).addSuccessfulRecovery(player);
    }

    @Test
    void shouldOfferPasswordDialogOnlyAfterValidCode() {
        // given
        Player player = mockPlayer();
        givenDialogFlowEnabled(true);
        given(dialogAdapter.isDialogSupported()).willReturn(true);
        given(emailService.hasAllInformation()).willReturn(true);
        given(recoveryCodeService.hasTriesLeft(NAME)).willReturn(true);
        given(recoveryCodeService.isCodeValid(NAME, "123abc")).willReturn(true);
        givenDialogTextStubs(player);

        // when
        service.submitCode(player, "123abc");

        // then
        verify(commonService).send(player, MessageKey.RECOVERY_CODE_CORRECT);
        verify(passwordRecoveryService).addSuccessfulRecovery(player);
        verify(recoveryCodeService).removeCode(NAME);
        verify(dialogAdapter).showNewPasswordDialog(eq(player), any(DialogWindowSpec.class));
    }

    @Test
    void shouldLeaveChatFallbackUntouchedWhenFlowIsDisabled() {
        // given
        Player player = mockPlayer();
        given(commonService.getProperty(RegistrationSettings.DIALOG_EMAIL_RECOVERY)).willReturn(false);
        givenValidEmailInput();
        given(recoveryCodeService.isRecoveryCodeNeeded()).willReturn(true);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        boolean enabled = service.isDialogFlowEnabled();
        service.requestRecovery(player, EMAIL);

        // then
        assertThat(enabled, is(false));
        verify(passwordRecoveryService).createAndSendRecoveryCode(player, EMAIL);
        verifyNoInteractions(dialogAdapter);
    }

    @Test
    void shouldNotRevealWhetherTheEmailExistsOrMatches() {
        // given
        Player player = mockPlayer();
        given(emailService.hasAllInformation()).willReturn(true);
        given(playerCache.isAuthenticated(NAME)).willReturn(false);
        given(validationService.validateEmail("wrong@example.com")).willReturn(true);
        given(dataSource.getEmail(NAME)).willReturn(DataSourceValueImpl.unknownRow());

        // when
        service.requestRecovery(player, "wrong@example.com");

        // then
        verify(commonService).send(player, MessageKey.USAGE_REGISTER);
        verifyNoInteractions(passwordRecoveryService, dialogAdapter);
    }

    @Test
    void shouldRejectAuthenticatedPlayerWithoutContactingRecoveryService() {
        // given
        Player player = mockPlayer();
        given(emailService.hasAllInformation()).willReturn(true);
        given(playerCache.isAuthenticated(NAME)).willReturn(true);

        // when
        service.requestRecovery(player, EMAIL);

        // then
        verify(commonService).send(player, MessageKey.ALREADY_LOGGED_IN_ERROR);
        verifyNoInteractions(passwordRecoveryService, dialogAdapter, bukkitService);
    }

    @Test
    void shouldBuildCodeAndPasswordDialogSpecs() {
        // given
        Player player = mockPlayer();
        givenDialogTextStubs(player);

        // when
        service.showCodeDialog(player);
        service.showNewPasswordDialog(player);

        // then
        ArgumentCaptor<DialogWindowSpec> specs = ArgumentCaptor.forClass(DialogWindowSpec.class);
        verify(dialogAdapter).showRecoveryCodeDialog(eq(player), specs.capture());
        verify(dialogAdapter).showNewPasswordDialog(eq(player), specs.capture());
        assertThat(specs.getAllValues().get(0).title(), is(MessageKey.DIALOG_RECOVERY_CODE_TITLE.getKey()));
        assertThat(specs.getAllValues().get(0).body(), is(MessageKey.DIALOG_RECOVERY_CODE_BODY.getKey()));
        assertThat(specs.getAllValues().get(0).inputs().stream().map(input -> input.id()).toList(), contains("code"));
        assertThat(specs.getAllValues().get(1).title(), is(MessageKey.DIALOG_NEW_PASSWORD_TITLE.getKey()));
        assertThat(specs.getAllValues().get(1).inputs().stream().map(input -> input.id()).toList(), contains("password"));
    }

    private Player mockPlayer() {
        Player player = mock(Player.class);
        lenient().when(player.getName()).thenReturn(NAME);
        return player;
    }

    private void givenDialogTextStubs(Player player) {
        given(commonService.retrieveSingleMessage(eq(player), any(MessageKey.class)))
            .willAnswer(invocation -> invocation.getArgument(1, MessageKey.class).getKey());
        given(commonService.getProperty(RegistrationSettings.DIALOG_SHOW_BODY)).willReturn(true);
        given(commonService.getProperty(RegistrationSettings.DIALOG_STACK_BUTTONS_VERTICALLY)).willReturn(false);
    }

    private void givenDialogFlowEnabled(boolean enabled) {
        given(commonService.getProperty(RegistrationSettings.DIALOG_EMAIL_RECOVERY)).willReturn(enabled);
    }

    private void givenValidEmailInput() {
        given(emailService.hasAllInformation()).willReturn(true);
        given(playerCache.isAuthenticated(NAME)).willReturn(false);
        given(validationService.validateEmail(EMAIL)).willReturn(true);
        given(dataSource.getEmail(NAME)).willReturn(DataSourceValueImpl.of(EMAIL));
    }
}
