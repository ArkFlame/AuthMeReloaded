package fr.xephi.authme.data.limbo.persistence;

/**
 * Creates segment names for {@link DistributedFilesPersistenceHandler}.
 */
class SegmentNameBuilder {

    private final int length;
    private final int distribution;
    private final String prefix;
    private final char[] segmentChars;

    /**
     * Constructor.
     *
     * @param partition the segment configuration
     */
    SegmentNameBuilder(SegmentSize partition) {
        this.length = partition.getLength();
        this.distribution = partition.getDistribution();
        this.prefix = "seg" + partition.getTotalSegments() + "-";
        this.segmentChars = distribution == 16 ? null : buildCharMap(distribution);
    }

    /**
     * Returns the segment ID for the given UUID.
     *
     * @param uuid the player's uuid to get the segment for
     * @return id the uuid belongs to
     */
    String createSegmentName(String uuid) {
        String uuidPrefix = uuid.substring(0, length);
        return prefix + (distribution == 16 ? uuidPrefix : buildSegmentName(uuidPrefix));
    }

    /**
     * @return the prefix used for the current segment configuration
     */
    String getPrefix() {
        return prefix;
    }

    private String buildSegmentName(String uuidPrefix) {
        StringBuilder sb = new StringBuilder(uuidPrefix.length());
        for (int i = 0; i < uuidPrefix.length(); ++i) {
            sb.append(segmentChars[uuidPrefix.charAt(i)]);
        }
        return sb.toString();
    }

    private static char[] buildCharMap(int distribution) {
        final char[] hexChars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        final int divisor = 16 / distribution;
        char[] charToSegmentChar = new char['f' + 1];
        for (int i = 0; i < hexChars.length; ++i) {
            charToSegmentChar[hexChars[i]] = hexChars[i / divisor];
        }
        return charToSegmentChar;
    }
}
