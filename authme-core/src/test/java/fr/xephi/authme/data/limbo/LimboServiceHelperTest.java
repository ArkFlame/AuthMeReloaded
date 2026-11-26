package fr.xephi.authme.data.limbo;

import fr.xephi.authme.TestHelper;
import org.bukkit.World;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link LimboServiceHelper}.
 * <p>
 * Note: some methods are tested directly where they are used via {@link LimboServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class LimboServiceHelperTest {

    @InjectMocks
    private LimboServiceHelper limboServiceHelper;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    private static Stream<Arguments> mergeCases() {
        Location newLocation = mock(Location.class);
        Location oldLocation = mock(Location.class);
        return Stream.of(
            Arguments.of(
                new LimboPlayer(newLocation, false, Collections.singletonList(new UserGroup("grp-new")), false, 0.0f, 0.0f),
                new LimboPlayer(oldLocation, true, Collections.singletonList(new UserGroup("grp-old")), true, 0.1f, 0.8f),
                oldLocation, true, new UserGroup("grp-old"), true, 0.0f, 0.0f),
            Arguments.of(
                new LimboPlayer(newLocation, false, Collections.singletonList(new UserGroup("grp-new")), true, 0.3f, 0.0f),
                new LimboPlayer(null, false, Collections.emptyList(), false, 0.1f, 0.1f),
                newLocation, false, new UserGroup("grp-new"), true, 0.3f, 0.0f));
    }

    @ParameterizedTest
    @MethodSource("mergeCases")
    void shouldMergeLimboPlayers(LimboPlayer newLimbo, LimboPlayer oldLimbo, Location expectedLocation,
                                 boolean expectedOperator, UserGroup expectedGroup, boolean expectedCanFly,
                                 float expectedWalkSpeed, float expectedFlySpeed) {
        // when
        LimboPlayer result = limboServiceHelper.merge(newLimbo, oldLimbo);

        // then
        assertThat(result.getLocation(), equalTo(expectedLocation));
        assertThat(result.isOperator(), equalTo(expectedOperator));
        assertThat(result.getGroups(), contains(expectedGroup));
        assertThat(result.isCanFly(), equalTo(expectedCanFly));
        assertThat(result.getWalkSpeed(), equalTo(expectedWalkSpeed));
        assertThat(result.getFlySpeed(), equalTo(expectedFlySpeed));
    }

    @Test
    void shouldMergeEnderPearlRestoreData() {
        // given
        World world = mock(World.class);
        Location oldLocation = new Location(world, 1.0, 2.0, 3.0);
        Location newLocation = new Location(world, 4.0, 5.0, 6.0);
        UUID sharedUuid = UUID.nameUUIDFromBytes("shared-pearl".getBytes());
        UUID oldUuid = UUID.nameUUIDFromBytes("old-pearl".getBytes());
        UUID newUuid = UUID.nameUUIDFromBytes("new-pearl".getBytes());

        LimboPlayer newLimbo = new LimboPlayer(null, false, Collections.emptyList(), false, 0.0f, 0.0f);
        newLimbo.setEnderPearls(java.util.Arrays.asList(
            new EnderPearlRestoreData(sharedUuid, newLocation, null),
            new EnderPearlRestoreData(newUuid, newLocation, null)));

        LimboPlayer oldLimbo = new LimboPlayer(null, false, Collections.emptyList(), false, 0.0f, 0.0f);
        oldLimbo.setEnderPearls(java.util.Arrays.asList(
            new EnderPearlRestoreData(sharedUuid, oldLocation, null),
            new EnderPearlRestoreData(oldUuid, oldLocation, null)));

        // when
        LimboPlayer result = limboServiceHelper.merge(newLimbo, oldLimbo);

        // then
        assertThat(result.getEnderPearlUuids(), containsInAnyOrder(sharedUuid, oldUuid, newUuid));
        java.util.Map<UUID, EnderPearlRestoreData> pearlsByUuid = result.getEnderPearls().stream()
            .collect(java.util.stream.Collectors.toMap(EnderPearlRestoreData::getUuid, pearl -> pearl));
        assertThat(pearlsByUuid.get(sharedUuid).getLocation(), equalTo(newLocation));
        assertThat(pearlsByUuid.get(oldUuid).getLocation(), equalTo(oldLocation));
        assertThat(pearlsByUuid.get(newUuid).getLocation(), equalTo(newLocation));
    }

    @Test
    void shouldHandleNullInputs() {
        // given
        LimboPlayer limbo = mock(LimboPlayer.class);

        // when
        LimboPlayer result1 = limboServiceHelper.merge(limbo, null);
        LimboPlayer result2 = limboServiceHelper.merge(null, limbo);
        LimboPlayer result3 = limboServiceHelper.merge(null, null);

        // then
        verifyNoInteractions(limbo);
        assertThat(result1, equalTo(limbo));
        assertThat(result2, equalTo(limbo));
        assertThat(result3, nullValue());
    }
}
