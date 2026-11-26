package fr.xephi.authme.util;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.util.ConcurrentModificationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Test for {@link ExceptionUtils}.
 */
class ExceptionUtilsTest {

    @Test
    void shouldFindWantedThrowable() {
        Throwable th = new Throwable(new ConcurrentModificationException());
        ConcurrentModificationException cme = new ConcurrentModificationException(th);
        IllegalStateException ise = new IllegalStateException(cme);
        UnsupportedOperationException uoe = new UnsupportedOperationException(ise);
        ReflectiveOperationException roe = new ReflectiveOperationException(uoe);

        IllegalStateException resultIse = ExceptionUtils.findThrowableInCause(IllegalStateException.class, roe);
        ConcurrentModificationException resultCme = ExceptionUtils.findThrowableInCause(ConcurrentModificationException.class, cme);
        StackOverflowError resultSoe = ExceptionUtils.findThrowableInCause(StackOverflowError.class, cme);

        assertThat(resultIse, sameInstance(ise));
        assertThat(resultCme, sameInstance(cme));
        assertThat(resultSoe, nullValue());
    }

    @Test
    void shouldHandleCircularCausesGracefully() {
        ExceptionWithSettableCause exceptionWithSettableCause = new ExceptionWithSettableCause();
        UnsupportedOperationException uoe = new UnsupportedOperationException(exceptionWithSettableCause);
        ReflectiveOperationException roe = new ReflectiveOperationException(uoe);
        exceptionWithSettableCause.cause = roe;

        NullPointerException resultNpe = ExceptionUtils.findThrowableInCause(NullPointerException.class, uoe);
        UnsupportedOperationException resultUoe = ExceptionUtils.findThrowableInCause(UnsupportedOperationException.class, uoe);

        assertThat(resultNpe, nullValue());
        assertThat(resultUoe, sameInstance(uoe));
    }

    @Test
    void shouldFormatException() {
        MalformedURLException ex = new MalformedURLException("Unrecognized URL format");

        String result = ExceptionUtils.formatException(ex);

        assertThat(result, equalTo("[MalformedURLException]: Unrecognized URL format"));
    }

    private static final class ExceptionWithSettableCause extends Exception {

        Exception cause;

        @Override
        public synchronized Throwable getCause() {
            return cause;
        }
    }
}
