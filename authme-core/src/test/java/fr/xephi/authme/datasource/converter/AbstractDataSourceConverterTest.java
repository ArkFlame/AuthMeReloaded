package fr.xephi.authme.datasource.converter;

import fr.xephi.authme.data.auth.PlayerAuth;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.datasource.DataSourceType;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Test for {@link AbstractDataSourceConverter}.
 */
class AbstractDataSourceConverterTest extends AbstractConverterTest {

    @Test
    void shouldThrowForDestinationTypeMismatch() {
        DataSource destination = mockDestination(DataSourceType.MYSQL);
        DataSourceType destinationType = DataSourceType.SQLITE;
        DataSource source = mock(DataSource.class);
        Converter converter = new DataSourceConverterTestImpl<>(source, destination, destinationType);
        CommandSender sender = mock(CommandSender.class);

        converter.execute(sender);

        verify(sender).sendMessage(argThat(containsString("Please configure your connection to SQLITE")));
        verify(destination, only()).getType();
        verifyNoInteractions(source);
    }

    @Test
    void shouldHandleSourceThrowingException() {
        DataSource source = mock(DataSource.class);
        DataSourceType destinationType = DataSourceType.SQLITE;
        DataSource destination = mockDestination(destinationType);
        DataSourceConverterTestImpl<DataSource> converter =
            Mockito.spy(new DataSourceConverterTestImpl<>(source, destination, destinationType));
        doThrow(IllegalStateException.class).when(converter).getSource();
        CommandSender sender = mock(CommandSender.class);

        converter.execute(sender);

        verify(sender).sendMessage("The data source to convert from could not be initialized");
        verify(destination, only()).getType();
        verifyNoInteractions(source);
    }

    @Test
    void shouldConvertAndSkipExistingPlayers() {
        DataSource source = mock(DataSource.class);
        DataSourceType destinationType = DataSourceType.MYSQL;
        DataSource destination = mockDestination(destinationType);

        List<PlayerAuth> auths =
            Arrays.asList(mockAuthWithName("Steven"), mockAuthWithName("bobby"), mockAuthWithName("Jack"));
        given(source.getAllAuths()).willReturn(auths);
        given(destination.isAuthAvailable(auths.get(0).getNickname())).willReturn(true);

        Converter converter = new DataSourceConverterTestImpl<>(source, destination, destinationType);
        CommandSender sender = mock(CommandSender.class);

        converter.execute(sender);

        verify(destination).getType();
        verify(destination, times(3)).isAuthAvailable(anyString());
        verify(destination, times(2)).saveAuth(any(PlayerAuth.class));
        verify(destination, times(2)).updateSession(any(PlayerAuth.class));
        verify(destination, times(2)).updateQuitLoc(any(PlayerAuth.class));
        verifyNoMoreInteractions(destination);
        verify(sender).sendMessage(argThat(containsString(auths.get(0).getNickname())));
        verify(sender).sendMessage(argThat(containsString("successfully converted")));
    }

    private static DataSource mockDestination(DataSourceType returnedType) {
        DataSource destination = mock(DataSource.class);
        given(destination.getType()).willReturn(returnedType);
        return destination;
    }

    private static PlayerAuth mockAuthWithName(String name) {
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getNickname()).willReturn(name);
        return auth;
    }

    private static class DataSourceConverterTestImpl<S extends DataSource> extends AbstractDataSourceConverter<S> {

        private final S source;

        DataSourceConverterTestImpl(S source, DataSource destination, DataSourceType destinationType) {
            super(destination, destinationType);
            this.source = source;
        }

        @Override
        protected S getSource() {
            return source;
        }
    }
}
