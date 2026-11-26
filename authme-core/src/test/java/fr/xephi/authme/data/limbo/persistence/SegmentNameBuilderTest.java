package fr.xephi.authme.data.limbo.persistence;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static fr.xephi.authme.data.limbo.persistence.SegmentSize.EIGHT;
import static fr.xephi.authme.data.limbo.persistence.SegmentSize.FOUR;
import static fr.xephi.authme.data.limbo.persistence.SegmentSize.ONE;
import static fr.xephi.authme.data.limbo.persistence.SegmentSize.SIXTEEN;
import static fr.xephi.authme.data.limbo.persistence.SegmentSize.SIXTY_FOUR;
import static fr.xephi.authme.data.limbo.persistence.SegmentSize.THIRTY_TWO;
import static fr.xephi.authme.data.limbo.persistence.SegmentSize.TWO_FIFTY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test for {@link SegmentNameBuilder}.
 */
class SegmentNameBuilderTest {

    /**
     * Checks that using a given segment size really produces as many segments as defined.
     * E.g. if we partition with {@link SegmentSize#EIGHT} we expect eight different buckets.
     */
    @Test
    void shouldCreatePromisedSizeOfSegments() {
        for (SegmentSize part : SegmentSize.values()) {
            // Perform this check only for `length` <= 5 because the test creates all hex numbers with `length` digits.
            if (part.getLength() <= 5) {
                checkTotalSegmentsProduced(part);
            }
        }
    }

    private void checkTotalSegmentsProduced(SegmentSize part) {
        // given
        SegmentNameBuilder nameBuilder = new SegmentNameBuilder(part);
        Set<String> encounteredSegments = new HashSet<>();
        int shift = part.getLength() * 4;
        // e.g. (1 << 16) - 1 = 0xFFFF. (Number of digits = shift/4, since 16 = 2^4)
        int max = (1 << shift) - 1;

        // when
        for (int i = 0; i <= max; ++i) {
            String uuid = toPaddedHex(i, part.getLength());
            encounteredSegments.add(nameBuilder.createSegmentName(uuid));
        }

        // then
        assertThat(encounteredSegments, hasSize(part.getTotalSegments()));
    }

    private static String toPaddedHex(int dec, int padLength) {
        String hexResult = Integer.toString(dec, 16);
        while (hexResult.length() < padLength) {
            hexResult = "0" + hexResult;
        }
        return hexResult;
    }

    @Test
    void shouldCreateOneSegment() {
        assertSegments(ONE, "abc", "seg1-0", "f0e", "seg1-0", "329", "seg1-0");
    }

    @Test
    void shouldCreateFourSegments() {
        assertSegments(FOUR, "f9cc", "seg4-3", "84c9", "seg4-2", "3799", "seg4-0");
    }

    @Test
    void shouldCreateEightSegments() {
        assertSegments(EIGHT, "fc9c", "seg8-7", "90ad", "seg8-4", "35e4", "seg8-1", "a39f", "seg8-5");
    }

    @Test
    void shouldCreateSixteenSegments() {
        assertSegments(SIXTEEN, "fc9a054", "seg16-f", "b0a945e", "seg16-b", "7afebab", "seg16-7");
    }

    @Test
    void shouldCreateThirtyTwoSegments() {
        assertSegments(THIRTY_TWO, "f890c9", "seg32-11101", "49c39a", "seg32-01101", "b75d09", "seg32-10010");
    }

    @Test
    void shouldCreateSixtyFourSegments() {
        assertSegments(SIXTY_FOUR, "82f", "seg64-203", "9b4", "seg64-221", "068", "seg64-012");
    }

    @Test
    void shouldCreate256Segments() {
        assertSegments(TWO_FIFTY, "a813c", "seg256-a8", "b4d01", "seg256-b4", "7122f", "seg256-71");
    }

    private static void assertSegments(SegmentSize size, String... uuidAndExpectedSegment) {
        SegmentNameBuilder nameBuilder = new SegmentNameBuilder(size);
        for (int i = 0; i < uuidAndExpectedSegment.length; i += 2) {
            assertThat(nameBuilder.createSegmentName(uuidAndExpectedSegment[i]),
                equalTo(uuidAndExpectedSegment[i + 1]));
        }
    }
}
