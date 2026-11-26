package fr.xephi.authme.data.limbo.persistence;

/**
 * Configuration for the total number of segments to use.
 * <p>
 * The {@link DistributedFilesPersistenceHandler} reduces the number of files by assigning each UUID
 * to a segment, and this enum defines how many segments the UUIDs are distributed in.
 * <p>
 * A segment configuration consists of a <b>distribution</b> and a <b>length.</b> The distribution
 * defines to how many outputs a single hexadecimal character is mapped; e.g. a distribution of 3
 * distributes the characters 0-f evenly over three outputs. {@link SegmentNameBuilder} uses
 * hexadecimal characters as outputs, so with a distribution of 3 the characters 0-f are mapped to
 * 0, 1, or 2. To ensure an even distribution the segments must be powers of 2: a distribution of
 * 16 returns the input character unchanged, a distribution of 1 always returns the same output.
 * <p>
 * The <b>length</b> defines how many characters of a player's UUID are used to create the segment
 * ID. With a distribution of 2 and a length of 3, the first three characters are considered and
 * each mapped to one of two possible characters, so a UUID starting with "0f5c9321" may yield the
 * segment ID "010." Such a segment ID defines in which file the given UUID is found and stored.
 * <p>
 * The number of segments is {@code distribution ^ length}: a distribution of 2 and a length of 3
 * yields the segment IDs 000, 001, 010, 011, 100, 101, 110 and 111 (all binary numbers from 0 to 7).
 * A segment total may be achieved in multiple ways, e.g. 8 segments via distribution 8 and
 * length 1, or distribution 2 and length 3. Where possible, prefer a length of 1 (no string
 * concatenation required) or a distribution of 16 (no remapping of the characters required).
 */
public enum SegmentSize {

    /** 1. */
    ONE(1, 1),

    /** 4. */
    FOUR(4, 1),

    /** 8. */
    EIGHT(8, 1),

    /** 16. */
    SIXTEEN(16, 1),

    /** 32. */
    THIRTY_TWO(2, 5),

    /** 64. */
    SIXTY_FOUR(4, 3),

    /** 128. */
    ONE_TWENTY(2, 7),

    /** 256. */
    TWO_FIFTY(16, 2);

    private final int distribution, length;

    SegmentSize(int distribution, int length) {
        this.distribution = distribution;
        this.length = length;
    }

    /**
     * @return the distribution size per character, i.e. how many possible outputs there are
     *         for any hexadecimal character
     */
    public int getDistribution() {
        return distribution;
    }

    /**
     * @return number of characters from a UUID that should be used to create a segment ID
     */
    public int getLength() {
        return length;
    }

    /**
     * @return number of segments to which this configuration will distribute all UUIDs
     */
    public int getTotalSegments() {
        return (int) Math.pow(distribution, length);
    }
}
