package fr.xephi.authme.security.crypts;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * Shared itoa64 encoding used by the phpass-based WordPress and phpBB algorithms.
 */
final class PhpassCodec {

    /** The phpass itoa64 alphabet. */
    static final String ITOA64 = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private PhpassCodec() {
    }

    /**
     * Encodes the values provided by the given accessor into the itoa64 alphabet.
     *
     * @param valueAt provides the value at a given index
     * @param count the number of values to encode
     * @return the encoded string
     */
    static String encode64(IntUnaryOperator valueAt, int count) {
        StringBuilder output = new StringBuilder();
        int i = 0;
        do {
            int value = valueAt.applyAsInt(i++);
            output.append(ITOA64.charAt(value & 0x3f));
            if (i < count) {
                value |= valueAt.applyAsInt(i) << 8;
            }
            output.append(ITOA64.charAt((value >> 6) & 0x3f));
            if (i++ >= count) {
                break;
            }
            if (i < count) {
                value |= valueAt.applyAsInt(i) << 16;
            }
            output.append(ITOA64.charAt((value >> 12) & 0x3f));
            if (i++ >= count) {
                break;
            }
            output.append(ITOA64.charAt((value >> 18) & 0x3f));
        } while (i < count);
        return output.toString();
    }

    /**
     * Encodes bytes into the itoa64 alphabet, padding the input with zeroes up to {@code count} values.
     *
     * @param values the values to encode
     * @param count the number of values to encode
     * @return the encoded string
     */
    static String encode64(byte[] values, int count) {
        byte[] padded = values.length < count ? Arrays.copyOf(values, count) : values;
        return encode64(i -> padded[i] & 0xFF, count);
    }
}
