package fr.xephi.authme.service.bungeecord;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import fr.xephi.authme.AuthMe;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.settings.properties.HooksSettings;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockMakers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class BungeeSenderTest {

    @Mock(mockMaker = MockMakers.INLINE)
    private AuthMe plugin;

    @Mock(mockMaker = MockMakers.INLINE)
    private BukkitService bukkitService;

    @Mock(mockMaker = MockMakers.INLINE)
    private Settings settings;

    @Mock(mockMaker = MockMakers.INLINE)
    private Server server;

    @Mock(mockMaker = MockMakers.INLINE)
    private Messenger messenger;

    @Mock(mockMaker = MockMakers.INLINE)
    private Player carrier;

    @Captor
    private ArgumentCaptor<byte[]> payloadCaptor;

    @BeforeEach
    void setUp() {
        given(plugin.getServer()).willReturn(server);
        given(server.getMessenger()).willReturn(messenger);
    }

    @Test
    void shouldRegisterOutgoingChannelsWhenEnabled() {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        given(settings.getProperty(HooksSettings.BUNGEECORD_SERVER)).willReturn("");
        given(messenger.isOutgoingChannelRegistered(plugin, "BungeeCord")).willReturn(false);
        given(messenger.isOutgoingChannelRegistered(plugin, "authme:main")).willReturn(false);

        new BungeeSender(plugin, bukkitService, settings);

        verify(messenger).registerOutgoingPluginChannel(plugin, "BungeeCord");
        verify(messenger).registerOutgoingPluginChannel(plugin, "authme:main");
    }

    @Test
    void shouldUnregisterOutgoingChannelsWhenDisabledOnReload() {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true, false);
        given(settings.getProperty(HooksSettings.BUNGEECORD_SERVER)).willReturn("", "");
        given(messenger.isOutgoingChannelRegistered(plugin, "BungeeCord")).willReturn(false, true);
        given(messenger.isOutgoingChannelRegistered(plugin, "authme:main")).willReturn(false, true);

        BungeeSender bungeeSender = new BungeeSender(plugin, bukkitService, settings);
        bungeeSender.reload(settings);

        verify(messenger).registerOutgoingPluginChannel(plugin, "BungeeCord");
        verify(messenger).registerOutgoingPluginChannel(plugin, "authme:main");
        verify(messenger).unregisterOutgoingPluginChannel(plugin, "BungeeCord");
        verify(messenger).unregisterOutgoingPluginChannel(plugin, "authme:main");
    }

    private void givenEnabledSender() {
        given(settings.getProperty(HooksSettings.BUNGEECORD)).willReturn(true);
        given(settings.getProperty(HooksSettings.BUNGEECORD_SERVER)).willReturn("");
        given(messenger.isOutgoingChannelRegistered(plugin, "BungeeCord")).willReturn(true);
        given(messenger.isOutgoingChannelRegistered(plugin, "authme:main")).willReturn(true);
        given(plugin.isEnabled()).willReturn(true);
    }

    @ParameterizedTest
    @MethodSource("singleChunkCases")
    void shouldSendSingleChunk(List<String> names, String expectedField) {
        createSenderAndSendPremiumList(names);

        verify(bukkitService).sendAuthMePluginMessage(eq(carrier), payloadCaptor.capture());
        ByteArrayDataInput in = ByteStreams.newDataInput(payloadCaptor.getValue());
        assertEquals("premium.list.chunk", in.readUTF());
        assertEquals(expectedField, in.readUTF());
    }

    private static Stream<Arguments> singleChunkCases() {
        return Stream.of(
            Arguments.of(List.of(), "0:1:"),
            Arguments.of(List.of("alice", "bob", "charlie"), "0:1:alice,bob,charlie"),
            Arguments.of(List.of("Alice", "BOB"), "0:1:alice,bob"));
    }

    /**
     * Stubs an enabled sender and sends the given names as a premium list.
     */
    private void createSenderAndSendPremiumList(List<String> names) {
        givenEnabledSender();
        BungeeSender sender = new BungeeSender(plugin, bukkitService, settings);
        sender.sendPremiumList(carrier, names);
    }

    @Test
    void shouldSendTwoChunksFor1001Names() {
        List<String> names = IntStream.range(0, 1001)
            .mapToObj(i -> "player" + i)
            .collect(Collectors.toList());

        createSenderAndSendPremiumList(names);

        verify(bukkitService, times(2)).sendAuthMePluginMessage(eq(carrier), payloadCaptor.capture());
        List<byte[]> payloads = payloadCaptor.getAllValues();
        ByteArrayDataInput in0 = ByteStreams.newDataInput(payloads.get(0));
        assertEquals("premium.list.chunk", in0.readUTF());
        assertTrue(in0.readUTF().startsWith("0:0:"), "First chunk should not be last");
        ByteArrayDataInput in1 = ByteStreams.newDataInput(payloads.get(1));
        assertEquals("premium.list.chunk", in1.readUTF());
        assertTrue(in1.readUTF().startsWith("1:1:"), "Second chunk should be last");
    }
}
