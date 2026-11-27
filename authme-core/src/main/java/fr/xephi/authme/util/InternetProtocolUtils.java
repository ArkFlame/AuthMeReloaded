package fr.xephi.authme.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility class about the InternetProtocol
 */
public final class InternetProtocolUtils {

    // Utility class
    private InternetProtocolUtils() {
    }

    /**
     * Checks if the specified address is a loopback address. This can be one of the following:
     * <ul>
     * <li>127.0.0.1</li>
     * <li>localhost</li>
     * <li>[::1]</li>
     * </ul>
     *
     * @param address address to check
     * @return true if the address is a loopback one
     */
    public static boolean isLoopbackAddress(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
