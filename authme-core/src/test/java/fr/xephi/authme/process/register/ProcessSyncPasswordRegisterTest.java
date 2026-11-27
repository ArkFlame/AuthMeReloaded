package fr.xephi.authme.process.register;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.data.limbo.LimboService;
import fr.xephi.authme.events.RegisterEvent;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.service.bungeecord.BungeeSender;
import fr.xephi.authme.settings.commandconfig.CommandManager;
import fr.xephi.authme.settings.properties.EmailSettings;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link ProcessSyncPasswordRegister}.
 */
@ExtendWith(MockitoExtension.class)
class ProcessSyncPasswordRegisterTest {

    @InjectMocks
    private ProcessSyncPasswordRegister process;

    @Mock
    private BungeeSender bungeeSender;
    @Mock
    private CommonService service;
    @Mock
    private LimboService limboService;
    @Mock
    private CommandManager commandManager;
    @Mock
    private BukkitService bukkitService;
    @Mock
    private Player player;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @Test
    void shouldKickPlayerWithoutRunningRegisterCommands() {
        given(service.getProperty(EmailSettings.MAIL_ACCOUNT)).willReturn("");
        given(service.getProperty(RegistrationSettings.FORCE_KICK_AFTER_REGISTER)).willReturn(true);
        given(service.retrieveSingleMessage(player, MessageKey.REGISTER_SUCCESS)).willReturn("Registered");

        process.processPasswordRegister(player);

        verify(service).send(player, MessageKey.REGISTER_SUCCESS);
        verify(bukkitService).callEvent(any(RegisterEvent.class));
        verify(player).kickPlayer("Registered");
        verifyNoInteractions(commandManager, limboService, bungeeSender);
    }

    @Test
    void shouldAskForLoginAndEjectFromVehicleWhenLoginIsForced() {
        given(service.getProperty(EmailSettings.MAIL_ACCOUNT)).willReturn("mail@example.com");
        given(service.getProperty(RegistrationSettings.FORCE_KICK_AFTER_REGISTER)).willReturn(false);
        given(service.getProperty(RegistrationSettings.FORCE_LOGIN_AFTER_REGISTER)).willReturn(true);
        Entity vehicle = org.mockito.Mockito.mock(Entity.class);
        given(player.isInsideVehicle()).willReturn(true);
        given(player.getVehicle()).willReturn(vehicle);

        process.processPasswordRegister(player);

        verify(service).send(player, MessageKey.ADD_EMAIL_MESSAGE);
        verify(commandManager).runCommandsOnRegister(player);
        verify(limboService).replaceTasksAfterRegistration(player);
        verify(vehicle).eject();
        verify(bungeeSender, never()).connectPlayerOnLogin(player);
    }

    @Test
    void shouldHandOverToBungeeWhenNothingElseIsConfigured() {
        given(service.getProperty(EmailSettings.MAIL_ACCOUNT)).willReturn("");
        given(service.getProperty(RegistrationSettings.FORCE_KICK_AFTER_REGISTER)).willReturn(false);
        given(service.getProperty(RegistrationSettings.FORCE_LOGIN_AFTER_REGISTER)).willReturn(false);

        process.processPasswordRegister(player);

        verify(commandManager).runCommandsOnRegister(player);
        verify(bungeeSender).connectPlayerOnLogin(player);
        verifyNoInteractions(limboService);
        verify(service, never()).send(player, MessageKey.ADD_EMAIL_MESSAGE);
    }
}
