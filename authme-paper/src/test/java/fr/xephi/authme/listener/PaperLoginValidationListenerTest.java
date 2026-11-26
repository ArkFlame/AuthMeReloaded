package fr.xephi.authme.listener;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.message.Messages;
import fr.xephi.authme.service.PendingConnectionRegistry;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
public class PaperLoginValidationListenerTest {

    @InjectMocks
    private PaperLoginValidationListener listener;

    @Mock
    private OnJoinVerifier onJoinVerifier;

    @Mock
    private Messages messages;

    @Mock
    private PendingConnectionRegistry pendingConnectionRegistry;

    @Mock
    private Settings settings;

    private void givenConfiguredTimeouts() {
        given(settings.getProperty(RestrictionSettings.LOGIN_TIMEOUT)).willReturn(30);
        given(settings.getProperty(RestrictionSettings.REGISTER_TIMEOUT)).willReturn(30);
    }

    @Test
    public void shouldClaimNameDuringLoginPhase() {
        givenConfiguredTimeouts();
        PlayerLoginConnection connection = newLoginConnection("Bobby");
        given(pendingConnectionRegistry.tryClaim(eq("Bobby"), eq(connection), anyLong())).willReturn(true);
        PlayerConnectionValidateLoginEvent event = validate(connection);

        assertThat(event.isAllowed(), is(true));
        verify(pendingConnectionRegistry).tryClaim(eq("Bobby"), eq(connection), anyLong());
    }

    @Test
    public void shouldClaimNameForLongerThanTheGracePeriodWhenTimeoutIsDisabled() {
        given(settings.getProperty(RestrictionSettings.LOGIN_TIMEOUT)).willReturn(0);
        given(settings.getProperty(RestrictionSettings.REGISTER_TIMEOUT)).willReturn(30);
        PlayerLoginConnection connection = newLoginConnection("Bobby");
        given(pendingConnectionRegistry.tryClaim(eq("Bobby"), eq(connection), anyLong())).willReturn(true);

        validate(connection);

        verify(pendingConnectionRegistry).tryClaim("Bobby", connection, TimeUnit.DAYS.toMillis(1));
    }

    @Test
    public void shouldKickDuplicateSessionOnValidationEvent() throws FailedVerificationException {
        PlayerLoginConnection connection = newLoginConnection("Bobby");
        givenAlreadyOnlineMessage();
        willThrow(new FailedVerificationException(MessageKey.USERNAME_ALREADY_ONLINE_ERROR))
            .given(onJoinVerifier).checkSingleSession("Bobby");

        PlayerConnectionValidateLoginEvent event = validate(connection);

        assertThat(event.isAllowed(), is(false));
        assertThat(serialize(event.getKickMessage()), is("&cAlready online"));
        verify(pendingConnectionRegistry, never()).tryClaim(eq("Bobby"), eq(connection), anyLong());
    }

    @Test
    public void shouldRefuseSecondConnectionUsingSameNameDuringLoginPhase() {
        givenConfiguredTimeouts();
        PlayerLoginConnection connection = newLoginConnection("Bobby");
        given(pendingConnectionRegistry.tryClaim(eq("Bobby"), eq(connection), anyLong())).willReturn(false);
        givenAlreadyOnlineMessage();

        PlayerConnectionValidateLoginEvent event = validate(connection);

        assertThat(event.isAllowed(), is(false));
        assertThat(serialize(event.getKickMessage()), is("&cAlready online"));
    }

    @Test
    public void shouldIgnoreReconfigurationOfPlayerWithoutClaim() {
        PlayerConfigurationConnection connection = newConfigurationConnection("Bobby");
        given(pendingConnectionRegistry.holdsClaim("Bobby", connection)).willReturn(false);

        PlayerConnectionValidateLoginEvent event = validate(connection);

        verifyNoInteractions(onJoinVerifier, messages);
        assertThat(event.isAllowed(), is(true));
    }

    @Test
    public void shouldKickWhenClaimWasTakenOverDuringConfiguration() {
        PlayerConfigurationConnection connection = newConfigurationConnection("Bobby");
        given(pendingConnectionRegistry.isClaimedByOtherConnection("Bobby", connection)).willReturn(true);
        givenAlreadyOnlineMessage();

        PlayerConnectionValidateLoginEvent event = validate(connection);

        assertThat(event.isAllowed(), is(false));
        assertThat(serialize(event.getKickMessage()), is("&cAlready online"));
        verifyNoInteractions(onJoinVerifier);
    }

    @Test
    public void shouldVerifySingleSessionWhenFinishingConfiguration() throws FailedVerificationException {
        givenConfiguredTimeouts();
        PlayerConfigurationConnection connection = newConfigurationConnection("Bobby");
        given(pendingConnectionRegistry.holdsClaim("Bobby", connection)).willReturn(true);

        PlayerConnectionValidateLoginEvent event = validate(connection);

        assertThat(event.isAllowed(), is(true));
        verify(onJoinVerifier).checkSingleSession("Bobby");
        verify(pendingConnectionRegistry).tryClaim(eq("Bobby"), eq(connection), anyLong());
    }

    @Test
    public void shouldKickWhenNameWasTakenDuringConfiguration() throws FailedVerificationException {
        PlayerConfigurationConnection connection = newConfigurationConnection("Bobby");
        given(pendingConnectionRegistry.holdsClaim("Bobby", connection)).willReturn(true);
        givenAlreadyOnlineMessage();
        willThrow(new FailedVerificationException(MessageKey.USERNAME_ALREADY_ONLINE_ERROR))
            .given(onJoinVerifier).checkSingleSession("Bobby");

        PlayerConnectionValidateLoginEvent event = validate(connection);

        assertThat(event.isAllowed(), is(false));
        assertThat(serialize(event.getKickMessage()), is("&cAlready online"));
    }

    @Test
    public void shouldReleaseClaimOnJoin() {
        Player player = mock(Player.class);
        given(player.getName()).willReturn("Bobby");

        listener.onPlayerJoin(new PlayerJoinEvent(player, Component.text("joined")));

        verify(pendingConnectionRegistry).release("Bobby");
    }

    @Test
    public void shouldReleaseStaleClaimOnConnectionClose() {
        PlayerConnectionCloseEvent event = new PlayerConnectionCloseEvent(
            UUID.randomUUID(), "Bobby", new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 25565), false);

        listener.onPlayerConnectionClose(event);

        verify(pendingConnectionRegistry).releaseIfStale("Bobby");
    }

    @ParameterizedTest
    @MethodSource("serverFullCases")
    public void shouldHandleServerFullEvent(String playerName, String kickMessage, boolean allowed) {
        PlayerProfile profile = mock(PlayerProfile.class);
        given(profile.getName()).willReturn(playerName);
        given(onJoinVerifier.getServerFullKickMessageIfDenied(playerName)).willReturn(kickMessage);
        PlayerServerFullCheckEvent event = new PlayerServerFullCheckEvent(profile, Component.text("full"), true);

        listener.onPlayerServerFullCheck(event);

        assertThat(event.isAllowed(), is(allowed));
        if (!allowed) {
            assertThat(serialize(event.kickMessage()), is(kickMessage));
        }
        verify(onJoinVerifier).getServerFullKickMessageIfDenied(playerName);
    }

    private static Stream<Arguments> serverFullCases() {
        return Stream.of(
            Arguments.of("VipPlayer", null, true),
            Arguments.of("RegularPlayer", "&cServer full", false));
    }

    private void givenAlreadyOnlineMessage() {
        given(messages.retrieveSingle("Bobby", MessageKey.USERNAME_ALREADY_ONLINE_ERROR))
            .willReturn("&cAlready online");
    }

    private PlayerConnectionValidateLoginEvent validate(PlayerConnection connection) {
        PlayerConnectionValidateLoginEvent event = new PlayerConnectionValidateLoginEvent(connection, null);
        listener.onPlayerConnectionValidateLogin(event);
        return event;
    }

    private static PlayerLoginConnection newLoginConnection(String name) {
        PlayerLoginConnection connection = mock(PlayerLoginConnection.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        given(connection.getAuthenticatedProfile()).willReturn(profile);
        given(profile.getName()).willReturn(name);
        return connection;
    }

    private static PlayerConfigurationConnection newConfigurationConnection(String name) {
        PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        given(connection.getProfile()).willReturn(profile);
        given(profile.getName()).willReturn(name);
        return connection;
    }

    private static String serialize(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
