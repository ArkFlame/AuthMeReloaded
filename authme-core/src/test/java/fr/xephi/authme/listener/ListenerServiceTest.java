package fr.xephi.authme.listener;

import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.service.ValidationService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link ListenerService}.
 */
@ExtendWith(MockitoExtension.class)
class ListenerServiceTest {

    private ListenerService listenerService;

    @Mock
    private Settings settings;

    @Mock
    private DataSource dataSource;

    @Mock
    private PlayerCache playerCache;

    @Mock
    private ValidationService validationService;

    @BeforeEach
    void setUpMocksAndService() {
        given(settings.getProperty(RegistrationSettings.FORCE)).willReturn(true);
        listenerService = new ListenerService(settings, dataSource, playerCache, validationService);
    }

    @ParameterizedTest(name = "non-player entity: {0}")
    @MethodSource("nonPlayerEntities")
    void shouldHandleEventWithNonPlayerEntity(Entity entity) {
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(entity);

        boolean result = listenerService.shouldCancelEvent(event);

        assertThat(result, equalTo(false));
    }

    private static Stream<Entity> nonPlayerEntities() {
        return Stream.of(null, mock(Entity.class));
    }

    @ParameterizedTest(name = "authenticated={0}")
    @ValueSource(booleans = {true, false})
    void shouldCancelOnlyForUnauthenticatedPlayer(boolean authenticated) {
        String playerName = authenticated ? "Bobby" : "Tester";
        Player player = mockPlayerWithName(playerName);
        given(playerCache.isAuthenticated(playerName)).willReturn(authenticated);
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(player);

        boolean result = listenerService.shouldCancelEvent(event);

        assertThat(result, equalTo(!authenticated));
        verify(playerCache).isAuthenticated(playerName);
        // makes sure the setting is checked first = avoid unnecessary DB operation
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldAllowUnloggedPlayerForOptionalRegistration() {
        String playerName = "myPlayer1";
        Player player = mockPlayerWithName(playerName);
        given(playerCache.isAuthenticated(playerName)).willReturn(false);
        given(settings.getProperty(RegistrationSettings.FORCE)).willReturn(false);
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(player);
        listenerService.reload(settings);

        boolean result = listenerService.shouldCancelEvent(event);

        assertThat(result, equalTo(false));
        verify(playerCache).isAuthenticated(playerName);
        verify(dataSource).isAuthAvailable(playerName);
    }

    @Test
    void shouldAllowUnrestrictedName() {
        String playerName = "Npc2";
        Player player = mockPlayerWithName(playerName);
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(player);
        given(validationService.isUnrestricted(playerName)).willReturn(true);

        boolean result = listenerService.shouldCancelEvent(event);

        assertThat(result, equalTo(false));
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldAllowNpcPlayer() {
        String playerName = "other_npc";
        Player player = mockPlayerWithName(playerName);
        EntityEvent event = mock(EntityEvent.class);
        given(event.getEntity()).willReturn(player);
        given(player.hasMetadata("NPC")).willReturn(true);

        boolean result = listenerService.shouldCancelEvent(event);

        assertThat(result, equalTo(false));
        verify(player).hasMetadata("NPC");
    }

    @Test
    // This simply forwards to shouldCancelEvent(Player), so the rest is already tested
    void shouldHandlePlayerEvent() {
        String playerName = "example";
        Player player = mockPlayerWithName(playerName);
        PlayerEvent event = new TestPlayerEvent(player);
        given(playerCache.isAuthenticated(playerName)).willReturn(true);

        boolean result = listenerService.shouldCancelEvent(event);

        assertThat(result, equalTo(false));
        verify(playerCache).isAuthenticated(playerName);
        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldHandlePlayerEventWithNullPlayer() {
        PlayerEvent event = new TestPlayerEvent(null);

        boolean result = listenerService.shouldCancelEvent(event);

        assertThat(result, equalTo(false));
    }

    @Test
    // The previous tests verify most of shouldCancelEvent(Player)
    void shouldVerifyBasedOnPlayer() {
        String playerName = "player";
        Player player = mockPlayerWithName(playerName);

        boolean result = listenerService.shouldCancelEvent(player);

        assertThat(result, equalTo(true));
        verify(playerCache).isAuthenticated(playerName);
        verifyNoInteractions(dataSource);
    }


    /**
     * Test implementation of {@link PlayerEvent}.
     */
    private static final class TestPlayerEvent extends PlayerEvent {
        TestPlayerEvent(Player player) {
            super(player);
        }

        @Override
        public HandlerList getHandlers() {
            return null;
        }
    }
}
