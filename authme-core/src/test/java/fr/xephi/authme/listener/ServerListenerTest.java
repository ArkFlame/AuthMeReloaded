package fr.xephi.authme.listener;

import fr.xephi.authme.TestHelper;
import fr.xephi.authme.listener.packetevents.PacketEventsService;
import fr.xephi.authme.permission.PermissionsManager;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.PluginHookService;
import fr.xephi.authme.settings.SpawnLoader;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test for {@link ServerListener}.
 */
@ExtendWith(MockitoExtension.class)
class ServerListenerTest {

    private static final String ESSENTIALS = "Essentials";
    private static final String ESSENTIALS_SPAWN = "EssentialsSpawn";
    private static final String CMI = "CMI";
    private static final String MULTIVERSE = "Multiverse-Core";
    private static final String PACKET_EVENTS = "packetevents";

    @InjectMocks
    private ServerListener serverListener;

    @Mock
    private PermissionsManager permissionsManager;

    @Mock
    private PluginHookService pluginHookService;

    @Mock
    private PacketEventsService packetEventsService;

    @Mock
    private SpawnLoader spawnLoader;

    @Mock
    private BukkitService bukkitService;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @BeforeEach
    void runGlobalRegionTasksImmediately() {
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(bukkitService).runOnGlobalRegion(any(Runnable.class));
    }

    @Test
    void shouldDeferPluginHandlingToGlobalRegionThread() {
        List<Runnable> pendingTasks = new ArrayList<>();
        doAnswer(invocation -> {
            pendingTasks.add(invocation.getArgument(0));
            return null;
        }).when(bukkitService).runOnGlobalRegion(any(Runnable.class));

        PluginDisableEvent event = mockEventWithPluginName(PluginDisableEvent.class, PACKET_EVENTS);
        serverListener.onPluginDisable(event);

        // Nothing may be executed on the calling thread; the work is handed to the global region
        verifyNoInteractions(permissionsManager, packetEventsService);
        assertEquals(1, pendingTasks.size());

        pendingTasks.get(0).run();
        verify(permissionsManager).onPluginDisable(PACKET_EVENTS);
        verify(packetEventsService).disable();
    }

    @Test
    void shouldForwardPluginNameOnEnable() {
        checkPluginHandling(ESSENTIALS, true,       () -> verify(pluginHookService).tryHookToEssentials());
        checkPluginHandling(ESSENTIALS_SPAWN, true, () -> verify(spawnLoader).loadEssentialsSpawn());
        checkPluginHandling(CMI, true,              () -> {
            verify(pluginHookService).tryHookToCmi();
            verify(spawnLoader).loadCmiSpawn();
        });
        checkPluginHandling(MULTIVERSE, true,       () -> verify(pluginHookService).tryHookToMultiverse());
        checkPluginHandling(PACKET_EVENTS, true,    () -> verify(packetEventsService).setup());
        checkPluginHandling("UnknownPlugin", true,  () -> verifyNoInteractions(pluginHookService, spawnLoader));
    }

    @Test
    void shouldForwardPluginNameOnDisable() {
        checkPluginHandling(ESSENTIALS, false,       () -> verify(pluginHookService).unhookEssentials());
        checkPluginHandling(ESSENTIALS_SPAWN, false, () -> verify(spawnLoader).unloadEssentialsSpawn());
        checkPluginHandling(CMI, false,              () -> {
            verify(pluginHookService).unhookCmi();
            verify(spawnLoader).unloadCmiSpawn();
        });
        checkPluginHandling(MULTIVERSE, false,       () -> verify(pluginHookService).unhookMultiverse());
        checkPluginHandling(PACKET_EVENTS, false,    () -> verify(packetEventsService).disable());
        checkPluginHandling("UnknownPlugin", false,  () -> verifyNoInteractions(pluginHookService, spawnLoader));
    }

    private void checkPluginHandling(String pluginName, boolean enable, Runnable verifier) {
        if (enable) {
            serverListener.onPluginEnable(mockEventWithPluginName(PluginEnableEvent.class, pluginName));
        } else {
            serverListener.onPluginDisable(mockEventWithPluginName(PluginDisableEvent.class, pluginName));
        }
        verifier.run();
        if (enable) {
            verify(permissionsManager).onPluginEnable(pluginName);
        } else {
            verify(permissionsManager).onPluginDisable(pluginName);
        }
        verifyNoMoreInteractionsAndReset();
    }

    private void verifyNoMoreInteractionsAndReset() {
        verifyNoMoreInteractions(permissionsManager, pluginHookService, packetEventsService, spawnLoader);
        reset(permissionsManager, pluginHookService, packetEventsService, spawnLoader);
    }

    private static  <T extends PluginEvent> T mockEventWithPluginName(Class<T> eventClass, String name) {
        T event = mock(eventClass);
        Plugin plugin = mock(Plugin.class);
        given(plugin.getName()).willReturn(name);
        given(event.getPlugin()).willReturn(plugin);
        return event;
    }
}
