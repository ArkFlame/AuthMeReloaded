package fr.xephi.authme.util.lazytags;

import fr.xephi.authme.util.lazytags.TagReplacerTest.TestTagService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

/**
 * Test for {@link WrappedTagReplacer}.
 */
class WrappedTagReplacerTest {

    @ParameterizedTest
    @CsvSource({"8, i+i = 16", "1, i+i = 2"})
    void shouldApplyTags(int argument, String expectedDouble) {
        TestTagService tagService = new TestTagService();
        List<SampleClass> objects = Arrays.asList(
            new SampleClass(3, "pi is %PI"),
            new SampleClass(5, "no tags here"),
            new SampleClass(7, "i+i = %double"));

        WrappedTagReplacer<SampleClass, Integer> replacer = new WrappedTagReplacer<>(
            tagService.getAvailableTags(), objects, SampleClass::getDescription,
            (o, s) -> new SampleClass(o.number, s));
        List<SampleClass> result = replacer.getAdaptedItems(argument);

        assertThat(tagService.piCount, equalTo(1));
        assertThat(tagService.selfCount, equalTo(0));
        assertThat(tagService.doubleCount, equalTo(1));
        assertThat(tagService.squareCount, equalTo(0));
        assertThat(result, contains(
            new SampleClass(3, "pi is 3.14159"), new SampleClass(5, "no tags here"),
            new SampleClass(7, expectedDouble)));
    }

    private static final class SampleClass {
        private final int number;
        private final String description;

        SampleClass(int number, String description) {
            this.number = number;
            this.description = description;
        }

        String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof SampleClass)) {
                return false;
            }
            SampleClass other = (SampleClass) obj;
            return number == other.number && Objects.equals(description, other.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(number, description);
        }
    }
}
