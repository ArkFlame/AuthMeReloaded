package fr.xephi.authme.platform;

import fr.xephi.authme.data.auth.PlayerCache;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.listener.AdvancedShulkerFixListener;
import fr.xephi.authme.listener.BlockListener;
import fr.xephi.authme.listener.DoubleLoginFixListener;
import fr.xephi.authme.listener.EntityAirChangeListener;
import fr.xephi.authme.listener.EntityListener;
import fr.xephi.authme.listener.EntityPickupItemListener;
import fr.xephi.authme.listener.LegacyPlayerLoginListener;
import fr.xephi.authme.listener.LegacyPlayerPickupItemListener;
import fr.xephi.authme.listener.LegacyPlayerSpawnLocationListener;
import fr.xephi.authme.listener.LoginLocationFixListener;
import fr.xephi.authme.listener.PlayerListener;
import fr.xephi.authme.listener.PlayerSwapHandItemsListener;
import fr.xephi.authme.listener.ServerListener;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.PendingPremiumCache;
import fr.xephi.authme.service.PremiumLoginVerifier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AbstractSpigotPlatformAdapterTest {

    @Test
    void shouldUseBedSpawnLocationByDefault() {
        AbstractSpigotPlatformAdapter adapter = new AbstractSpigotPlatformAdapter() {
            @Override
            public String getPlatformName() {
                return "test";
            }
        };
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location bedSpawn = new Location(world, 10.0, 64.0, -2.0);
        given(player.getBedSpawnLocation()).willReturn(bedSpawn);

        Location result = adapter.getPlayerRespawnLocation(player);

        assertThat(result, equalTo(bedSpawn));
    }

    @Test
    void shouldCreatePacketInterceptionAdapterLazilyAndReuseIt() {
        TrackingPacketInterceptionAdapter packetInterceptionAdapter = new TrackingPacketInterceptionAdapter();
        TestSpigotPlatformAdapter adapter = new TestSpigotPlatformAdapter(packetInterceptionAdapter);
        PlayerCache playerCache = mock(PlayerCache.class);
        DataSource dataSource = mock(DataSource.class);
        Player player = mock(Player.class);

        adapter.sendBlankInventoryPacket(player);
        adapter.unregisterInventoryProtection();
        adapter.unregisterTabCompleteBlock();

        assertThat(adapter.createPacketInterceptionAdapterCalls, is(0));
        verifyNoInteractions(playerCache, dataSource);

        adapter.registerInventoryProtection(playerCache, dataSource);
        adapter.sendBlankInventoryPacket(player);
        adapter.registerTabCompleteBlock(playerCache);
        adapter.unregisterInventoryProtection();
        adapter.unregisterTabCompleteBlock();

        assertThat(adapter.createPacketInterceptionAdapterCalls, is(1));
        assertThat(packetInterceptionAdapter.inventoryProtectionRegistrations, is(1));
        assertThat(packetInterceptionAdapter.blankInventoryPacketsSent, is(1));
        assertThat(packetInterceptionAdapter.tabCompleteRegistrations, is(1));
        assertThat(packetInterceptionAdapter.inventoryProtectionUnregistrations, is(1));
        assertThat(packetInterceptionAdapter.tabCompleteUnregistrations, is(1));
    }

    // --- Listener selection matrix tests ---

    @ParameterizedTest
    @MethodSource("listenerMatrixParams")
    void shouldSelectListenersForCapabilities(Set<String> capabilities,
                                              List<Class<? extends Listener>> expected,
                                              List<Class<? extends Listener>> absent) {
        AbstractSpigotPlatformAdapter adapter = new CapabilityControlledAdapter(capabilities);
        List<Class<? extends Listener>> listeners = adapter.getListeners();

        assertThat(listeners, containsInAnyOrder(expected.toArray(new Class<?>[0])));
        for (Class<? extends Listener> listener : absent) {
            assertThat(listeners, not(hasItem(listener)));
        }
    }

    @Test
    void shouldNotIncludeAdvancedShulkerFixWhenPlayerCommandSendIsAvailable() {
        // 1.13+: the 1.8-only shulker fix must not register
        AbstractSpigotPlatformAdapter adapter = new CapabilityControlledAdapter(
            capsFor("org.bukkit.event.player.PlayerCommandSendEvent"));

        assertThat(adapter.getListeners(), not(hasItem(AdvancedShulkerFixListener.class)));
    }

    @Test
    void shouldNotIncludeLegacyPickupWhenModernPickupAvailable() {
        // Edge: only modern pickup available, no other optional APIs
        AbstractSpigotPlatformAdapter adapter = new CapabilityControlledAdapter(
            capsFor("org.bukkit.event.entity.EntityPickupItemEvent"));
        List<Class<? extends Listener>> listeners = adapter.getListeners();

        assertThat(listeners, hasItem(EntityPickupItemListener.class));
        assertThat(listeners, not(hasItem(LegacyPlayerPickupItemListener.class)));
    }

    private static Stream<Arguments> listenerMatrixParams() {
        // 1.8: no optional API classes available
        List<Class<? extends Listener>> listeners18 = baseListeners();

        // 1.9-1.10: spawn-location + swap-hand available, but not air-change or modern pickup
        List<Class<? extends Listener>> listeners19 = new ArrayList<>(listeners18);
        listeners19.add(LegacyPlayerSpawnLocationListener.class);
        listeners19.add(PlayerSwapHandItemsListener.class);

        // 1.11: adds air-change on top of the 1.9 set
        List<Class<? extends Listener>> listeners111 = new ArrayList<>(listeners19);
        listeners111.add(EntityAirChangeListener.class);

        // 1.12+: all optional APIs available -> modern pickup replaces legacy
        List<Class<? extends Listener>> listeners112 = new ArrayList<>(listeners111);
        listeners112.remove(LegacyPlayerPickupItemListener.class);
        listeners112.add(EntityPickupItemListener.class);

        return Stream.of(
            Arguments.of(capsFor(), listeners18, Arrays.asList(
                LegacyPlayerSpawnLocationListener.class, PlayerSwapHandItemsListener.class,
                EntityAirChangeListener.class, EntityPickupItemListener.class)),
            Arguments.of(capsFor(
                "org.spigotmc.event.player.PlayerSpawnLocationEvent",
                "org.bukkit.event.player.PlayerSwapHandItemsEvent"), listeners19,
                Arrays.asList(EntityAirChangeListener.class, EntityPickupItemListener.class)),
            Arguments.of(capsFor(
                "org.spigotmc.event.player.PlayerSpawnLocationEvent",
                "org.bukkit.event.player.PlayerSwapHandItemsEvent",
                "org.bukkit.event.entity.EntityAirChangeEvent"), listeners111,
                Collections.singletonList(EntityPickupItemListener.class)),
            Arguments.of(capsFor(
                "org.spigotmc.event.player.PlayerSpawnLocationEvent",
                "org.bukkit.event.player.PlayerSwapHandItemsEvent",
                "org.bukkit.event.entity.EntityAirChangeEvent",
                "org.bukkit.event.entity.EntityPickupItemEvent"), listeners112,
                Collections.singletonList(LegacyPlayerPickupItemListener.class)));
    }

    private static List<Class<? extends Listener>> baseListeners() {
        return Arrays.asList(
            PlayerListener.class,
            BlockListener.class,
            EntityListener.class,
            ServerListener.class,
            DoubleLoginFixListener.class,
            LoginLocationFixListener.class,
            AdvancedShulkerFixListener.class,
            LegacyPlayerLoginListener.class,
            LegacyPlayerPickupItemListener.class);
    }

    private static Set<String> capsFor(String... classNames) {
        return new HashSet<>(Arrays.asList(classNames));
    }

    private static final class CapabilityControlledAdapter extends AbstractSpigotPlatformAdapter {
        private final Set<String> available;

        CapabilityControlledAdapter(Set<String> available) {
            this.available = new HashSet<>(available);
        }

        @Override
        public String getPlatformName() {
            return "test-capability";
        }

        @Override
        protected boolean isClassAvailable(String className) {
            return available.contains(className);
        }
    }

    private static final class TestSpigotPlatformAdapter extends AbstractSpigotPlatformAdapter {

        private final PacketInterceptionAdapter packetInterceptionAdapter;
        private int createPacketInterceptionAdapterCalls;

        private TestSpigotPlatformAdapter(PacketInterceptionAdapter packetInterceptionAdapter) {
            this.packetInterceptionAdapter = packetInterceptionAdapter;
        }

        @Override
        public String getPlatformName() {
            return "test";
        }

        @Override
        protected PacketInterceptionAdapter createPacketInterceptionAdapter() {
            createPacketInterceptionAdapterCalls++;
            return packetInterceptionAdapter;
        }
    }

    private static final class TrackingPacketInterceptionAdapter implements PacketInterceptionAdapter {

        private int inventoryProtectionRegistrations;
        private int inventoryProtectionUnregistrations;
        private int blankInventoryPacketsSent;
        private int tabCompleteRegistrations;
        private int tabCompleteUnregistrations;

        @Override
        public void registerInventoryProtection(PlayerCache playerCache, DataSource dataSource) {
            inventoryProtectionRegistrations++;
        }

        @Override
        public void unregisterInventoryProtection() {
            inventoryProtectionUnregistrations++;
        }

        @Override
        public void sendBlankInventoryPacket(Player player) {
            blankInventoryPacketsSent++;
        }

        @Override
        public void registerTabCompleteBlock(PlayerCache playerCache) {
            tabCompleteRegistrations++;
        }

        @Override
        public void unregisterTabCompleteBlock() {
            tabCompleteUnregistrations++;
        }

        @Override
        public void registerPremiumVerification(DataSource dataSource, PremiumLoginVerifier verifier,
                                                PendingPremiumCache pendingPremiumCache, BukkitService bukkitService) {
        }

        @Override
        public void unregisterPremiumVerification() {
        }
    }
}
