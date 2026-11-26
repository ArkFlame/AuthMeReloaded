package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.FloodgateService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link UuidCommand}.
 */
@ExtendWith(MockitoExtension.class)
class UuidCommandTest {

    @InjectMocks
    private UuidCommand command;

    @Mock
    private DataSource dataSource;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private FloodgateService floodgateService;

    @Mock
    private CommonService commonService;

    @Mock
    private CommandSender sender;

    @Test
    void shouldShowOfflineUuidAndReportUnknownOtherUuids() {
        // given
        String playerName = "testUser";
        // when
        executeFor(playerName);
        // then
        verify(commonService).send(sender, MessageKey.ADMIN_UUID_OFFLINE, playerName, offlineUuid(playerName));
        verify(commonService).send(sender, MessageKey.ADMIN_UUID_MOJANG_UNKNOWN, playerName);
        verify(commonService).send(sender, MessageKey.ADMIN_UUID_FLOODGATE_UNKNOWN, playerName);
    }

    @Test
    void shouldShowStoredMojangUuid() {
        // given
        String playerName = "premiumUser";
        UUID mojangUuid = UUID.randomUUID();
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getPremiumUuid()).willReturn(mojangUuid);
        given(dataSource.getAuth(playerName)).willReturn(auth);
        // when
        executeFor(playerName);
        // then
        verify(commonService).send(sender, MessageKey.ADMIN_UUID_MOJANG, playerName, mojangUuid.toString());
        verify(commonService).send(sender, MessageKey.ADMIN_UUID_FLOODGATE_UNKNOWN, playerName);
    }

    @Test
    void shouldShowFloodgateUuidForOnlineBedrockPlayer() {
        // given
        String playerName = "bedrockUser";
        UUID floodgateUuid = UUID.randomUUID();
        Player player = mock(Player.class);
        given(player.getUniqueId()).willReturn(floodgateUuid);
        given(bukkitService.getPlayerExact(playerName)).willReturn(player);
        given(floodgateService.isFloodgatePlayer(floodgateUuid)).willReturn(true);
        // when
        executeFor(playerName);
        // then
        verify(commonService).send(sender, MessageKey.ADMIN_UUID_FLOODGATE, playerName, floodgateUuid.toString());
        verify(commonService).send(sender, MessageKey.ADMIN_UUID_MOJANG_UNKNOWN, playerName);
    }

    @Test
    void shouldNotShowFloodgateUuidForOnlineJavaPlayer() {
        // given
        String playerName = "javaUser";
        Player player = mock(Player.class);
        given(player.getUniqueId()).willReturn(UUID.randomUUID());
        given(bukkitService.getPlayerExact(playerName)).willReturn(player);
        // when
        executeFor(playerName);
        // then
        verify(commonService).send(sender, MessageKey.ADMIN_UUID_FLOODGATE_UNKNOWN, playerName);
    }

    @Test
    void shouldDefaultToSenderNameWithoutArguments() {
        // given
        given(sender.getName()).willReturn("consoleSender");
        // when
        command.executeCommand(sender, Collections.emptyList());
        // then
        verify(commonService).send(sender, MessageKey.ADMIN_UUID_OFFLINE, "consoleSender",
            offlineUuid("consoleSender"));
    }

    private static String offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName.toLowerCase(Locale.ROOT))
            .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void executeFor(String playerName) {
        command.executeCommand(sender, Collections.singletonList(playerName));
    }
}
