package fr.xephi.authme.security.totp;

import fr.xephi.authme.ReflectionTestUtils;
import fr.xephi.authme.security.totp.TotpAuthenticator.TotpGenerationResult;
import fr.xephi.authme.util.expiring.ExpiringMap;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static fr.xephi.authme.listener.TestPlayerUtils.mockPlayerWithName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link GenerateTotpService}.
 */
@ExtendWith(MockitoExtension.class)
class GenerateTotpServiceTest {

    @InjectMocks
    private GenerateTotpService generateTotpService;

    @Mock
    private TotpAuthenticator totpAuthenticator;


    @Test
    void shouldGenerateAndRemoveTotpKey() {
        TotpGenerationResult givenGenerationResult =
            new TotpGenerationResult("1234", "http://example.com/link/to/chart");
        Player player = mockPlayerWithName("Spencer");
        given(totpAuthenticator.generateTotpKey(player)).willReturn(givenGenerationResult);

        TotpGenerationResult result = generateTotpService.generateTotpKey(player);

        assertThat(result, equalTo(givenGenerationResult));
        assertThat(generateTotpService.getGeneratedTotpKey(player), equalTo(givenGenerationResult));

        generateTotpService.removeGenerateTotpKey(player);

        assertThat(generateTotpService.getGeneratedTotpKey(player), nullValue());
    }

    @Test
    void shouldCheckGeneratedTotpKey() {
        String generatedKey = "ASLO43KDF2J";
        TotpGenerationResult givenGenerationResult = new TotpGenerationResult(generatedKey, "url");
        Player player = mockPlayerWithName("Aria");
        given(totpAuthenticator.generateTotpKey(player)).willReturn(givenGenerationResult);
        generateTotpService.generateTotpKey(player);
        String validCode = "928374";
        given(totpAuthenticator.isValidCode(generatedKey, validCode)).willReturn(true);
        given(totpAuthenticator.isValidCode(generatedKey, "000000")).willReturn(false);

        boolean invalidCodeResult = generateTotpService.isTotpCodeCorrectForGeneratedTotpKey(player, "000000");
        boolean validCodeResult = generateTotpService.isTotpCodeCorrectForGeneratedTotpKey(player, validCode);
        boolean unknownPlayerResult = generateTotpService.isTotpCodeCorrectForGeneratedTotpKey(mockPlayerWithName("other"), "299874");

        assertThat(invalidCodeResult, equalTo(false));
        assertThat(validCodeResult, equalTo(true));
        assertThat(unknownPlayerResult, equalTo(false));
    }

    @Test
    void shouldVerifySetupCodeWithoutRecordingItForLogin() {
        String generatedKey = "ASLO43KDF2J";
        Player player = mockPlayerWithName("Nora");
        given(totpAuthenticator.generateTotpKey(player))
            .willReturn(new TotpGenerationResult(generatedKey, "url"));
        generateTotpService.generateTotpKey(player);
        String code = "123456";
        given(totpAuthenticator.isValidCode(generatedKey, code)).willReturn(true);

        boolean result = generateTotpService.isTotpCodeCorrectForGeneratedTotpKey(player, code);

        assertThat(result, equalTo(true));
        verify(totpAuthenticator).isValidCode(generatedKey, code);
        verify(totpAuthenticator, never()).checkCode(anyString(), anyString(), anyString());
    }

    @Test
    void shouldRemoveExpiredEntries() throws InterruptedException {
        TotpGenerationResult generationResult = new TotpGenerationResult("key", "url");
        ExpiringMap<String, TotpGenerationResult> generatedKeys =
            ReflectionTestUtils.getFieldValue(GenerateTotpService.class, generateTotpService, "totpKeys");
        generatedKeys.setExpiration(1, TimeUnit.MILLISECONDS);
        generatedKeys.put("ghost", generationResult);
        generatedKeys.setExpiration(5, TimeUnit.MINUTES);
        generatedKeys.put("ezra", generationResult);

        Thread.sleep(2L);
        generateTotpService.performCleanup();

        assertThat(generateTotpService.getGeneratedTotpKey(mockPlayerWithName("Ezra")), equalTo(generationResult));
        assertThat(generateTotpService.getGeneratedTotpKey(mockPlayerWithName("ghost")), nullValue());
    }
}
