package fr.xephi.authme.command.executable.authme;

import ch.jalu.injector.factory.Factory;
import fr.xephi.authme.TestHelper;
import fr.xephi.authme.datasource.converter.Converter;
import fr.xephi.authme.datasource.converter.CrazyLoginConverter;
import fr.xephi.authme.datasource.converter.LoginSecurityConverter;
import fr.xephi.authme.datasource.converter.RoyalAuthConverter;
import fr.xephi.authme.message.MessageKey;
import fr.xephi.authme.service.BukkitService;
import fr.xephi.authme.service.CommonService;
import fr.xephi.authme.util.StringUtils;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static fr.xephi.authme.service.BukkitServiceTestHelper.setBukkitServiceToRunTaskAsynchronously;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link ConverterCommand}.
 */
@ExtendWith(MockitoExtension.class)
class ConverterCommandTest {

    @InjectMocks
    private ConverterCommand command;

    @Mock
    private CommonService commonService;

    @Mock
    private BukkitService bukkitService;

    @Mock
    private Factory<Converter> converterFactory;

    @BeforeAll
    static void initLogger() {
        TestHelper.setupLogger();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidArguments")
    void shouldShowAvailableConvertersForInvalidInput(String caseName, List<String> arguments) {
        // given
        CommandSender sender = mock(CommandSender.class);

        // when
        command.executeCommand(sender, arguments);

        // then
        String converters = String.join(", ", ConverterCommand.CONVERTERS.keySet());
        verify(sender).sendMessage(argThat(containsString(converters)));
        verifyNoInteractions(commonService, converterFactory, bukkitService);
    }

    private static Stream<Arguments> invalidArguments() {
        return Stream.of(
            Arguments.of("no arguments", Collections.emptyList()),
            Arguments.of("unknown conversion type", Collections.singletonList("invalid")));
    }

    @Test
    void shouldHaveUniqueClassForEachConverter() {
        // given
        Set<Class<? extends Converter>> classes = new HashSet<>();

        // when / then
        for (Map.Entry<String, Class<? extends Converter>> entry : ConverterCommand.CONVERTERS.entrySet()) {
            assertThat("Name is not null or empty",
                StringUtils.isBlank(entry.getKey()), equalTo(false));

            assertThat("Converter class is unique for each entry",
                classes.add(entry.getValue()), equalTo(true));
        }
    }

    @Test
    void shouldHaveLegacyConverters() {
        // given / when / then
        assertThat(ConverterCommand.CONVERTERS.get("crazylogin"), equalTo(CrazyLoginConverter.class));
        assertThat(ConverterCommand.CONVERTERS.get("loginsecurity"), equalTo(LoginSecurityConverter.class));
        assertThat(ConverterCommand.CONVERTERS.get("royalauth"), equalTo(RoyalAuthConverter.class));
    }

    @Test
    void shouldLaunchConverterForAllTypes() {
        // given
        String converterName = "authplus";
        Class<? extends Converter> converterClass = ConverterCommand.CONVERTERS.get(converterName);
        Converter converter = createMockReturnedByInjector(converterClass);
        CommandSender sender = mock(CommandSender.class);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Collections.singletonList(converterName));

        // then
        verify(converter).execute(sender);
        verifyNoMoreInteractions(converter);
        verify(converterFactory).newInstance(converterClass);
        verifyNoMoreInteractions(converterFactory);
    }

    @Test
    void shouldCatchExceptionInConverterAndInformSender() {
        // given
        String converterName = "authplus";
        Class<? extends Converter> converterClass = ConverterCommand.CONVERTERS.get(converterName);
        Converter converter = createMockReturnedByInjector(converterClass);
        doThrow(IllegalStateException.class).when(converter).execute(any(CommandSender.class));
        CommandSender sender = mock(CommandSender.class);
        setBukkitServiceToRunTaskAsynchronously(bukkitService);

        // when
        command.executeCommand(sender, Collections.singletonList(converterName.toUpperCase(Locale.ROOT)));

        // then
        verify(converter).execute(sender);
        verifyNoMoreInteractions(converter);
        verify(converterFactory).newInstance(converterClass);
        verifyNoMoreInteractions(converterFactory);
        verify(commonService).send(sender, MessageKey.ERROR);
    }

    private <T extends Converter> T createMockReturnedByInjector(Class<T> clazz) {
        T converter = mock(clazz);
        given(converterFactory.newInstance(clazz)).willReturn(converter);
        return converter;
    }

}
