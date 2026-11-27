package fr.xephi.authme.process.logout;

import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.settings.properties.RegistrationSettings;
import fr.xephi.authme.settings.properties.RestrictionSettings;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void shouldApplyBlindnessForTheConfiguredTimeoutInTicks() {
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(true);
        given(service.getProperty(RestrictionSettings.LOGIN_TIMEOUT)).willReturn(30);
        PotionEffect effect = mock(PotionEffect.class);
        given(bukkitService.createBlindnessEffect(30 * BukkitService.TICKS_PER_SECOND)).willReturn(effect);

        LogoutActions.applyBlindEffect(player, service, bukkitService, RestrictionSettings.LOGIN_TIMEOUT);

        verify(player).addPotionEffect(effect);
    }

    @Test
    void shouldDoNothingWhenBlindnessIsDisabled() {
        given(service.getProperty(RegistrationSettings.APPLY_BLIND_EFFECT)).willReturn(false);

        LogoutActions.applyBlindEffect(player, service, bukkitService, RestrictionSettings.LOGIN_TIMEOUT);

        verifyNoInteractions(player, bukkitService);
    }
}
