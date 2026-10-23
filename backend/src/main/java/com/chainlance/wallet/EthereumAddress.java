package com.chainlance.wallet;

import java.util.Locale;
import java.util.regex.Pattern;
import org.web3j.crypto.Keys;

/**
 * Validates and normalizes Ethereum addresses. Addresses are always stored lowercase.
 * A mixed-case address must carry a valid EIP-55 checksum, which catches typos; all-lowercase
 * and all-uppercase addresses carry no checksum and are accepted.
 */
public final class EthereumAddress {

    private static final Pattern FORMAT = Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final String ZERO = "0x" + "0".repeat(40);

    private EthereumAddress() {
    }

    /** Returns the lowercase form, or throws IllegalArgumentException with a user-safe message. */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Wallet address is required");
        }
        String address = input.trim();
        if (!FORMAT.matcher(address).matches()) {
            throw new IllegalArgumentException("Wallet address must be 0x followed by 40 hexadecimal characters");
        }
        String hex = address.substring(2);
        boolean mixedCase = !hex.equals(hex.toLowerCase(Locale.ROOT)) && !hex.equals(hex.toUpperCase(Locale.ROOT));
        String lower = address.toLowerCase(Locale.ROOT);
        if (mixedCase && !address.equals(Keys.toChecksumAddress(lower))) {
            throw new IllegalArgumentException("Wallet address has an invalid checksum; check for typos");
        }
        if (lower.equals(ZERO)) {
            throw new IllegalArgumentException("The zero address cannot be linked");
        }
        return lower;
    }
}
