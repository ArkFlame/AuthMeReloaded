package fr.xephi.authme.util.lazytags;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

/**
 * Test for {@link TagBuilder}.
 */
class TagBuilderTest {

    @Test
    void shouldCreateNoArgsTag() {
        Tag<String> tag = TagBuilder.createTag("hey", () -> "hello");

        assertThat(tag, instanceOf(SimpleTag.class));
        assertThat(tag.getValue(null), equalTo("hello"));
    }

    @Test
    void shouldCreateDependentTag() {
        Tag<Double> tag = TagBuilder.createTag("%test", d -> Double.toString(d + d / 10));

        assertThat(tag, instanceOf(DependentTag.class));
        assertThat(tag.getValue(24d), equalTo("26.4"));
    }
}
