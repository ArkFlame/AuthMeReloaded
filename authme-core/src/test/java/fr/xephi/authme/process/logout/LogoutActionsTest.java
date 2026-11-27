package fr.xephi.authme.process.logout;

import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test for {@link LogoutActions}.
 */
@ExtendWith(MockitoExtension.class)
class LogoutActionsTest {

    @Mock
    private Player player;
    @Mock
    private CommonService service;
    @Mock
    private BukkitService bukkitService;

    @ParameterizedTest(name = "blind effect={0}")
    @ValueSource(booleans = {true, false})
    void shouldApplyBlindnessOnlyWhenEnabled(boolean applyBlindEffect) {
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(applyBlindEffect);
        PotionEffect effect = null;
        if (applyBlindEffect) {
            given(service.getProperty(RestrictionSettings.LOGIN_TIMEOUT)).willReturn(30);
            effect = mock(PotionEffect.class);
            given(bukkitService.createBlindnessEffect(30 * BukkitService.TICKS_PER_SECOND)).willReturn(effect);
        }
        LogoutActions.applyBlindEffect(player, service, bukkitService, RestrictionSettings.LOGIN_TIMEOUT);
        if (applyBlindEffect) {
            verify(player).addPotionEffect(effect);
        } else {
            verifyNoInteractions(player, bukkitService);
        }
    }
}
