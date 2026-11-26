package fr.xephi.authme.data.limbo.persistence;

/**
 * Configuration for the total number of segments to use.
 * <p>
 * The {@link DistributedFilesPersistenceHandler} reduces the number of files by assigning each UUID
 * to a segment, and this enum defines how many segments the UUIDs are distributed in.
 * <p>
 * A configuration consists of a <b>distribution</b> and a <b>length</b>. The distribution defines
 * how many outputs a hexadecimal character can map to; the length defines how many UUID characters
 * are used to create the segment ID. {@link SegmentNameBuilder} performs this mapping.
 * <p>
 * The number of segments is {@code distribution ^ length}. Where possible, prefer a length of 1
 * (no string concatenation required) or a distribution of 16 (no remapping required).
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
