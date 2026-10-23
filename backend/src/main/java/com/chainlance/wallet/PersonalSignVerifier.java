package com.chainlance.wallet;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Recovers the signer of an Ethereum "personal_sign" (EIP-191) message, which is what MetaMask's
 * signer.signMessage(text) produces. Pure computation: no keys, no network, no database.
 */
public final class PersonalSignVerifier {

    private static final Pattern SIGNATURE = Pattern.compile("^0x[0-9a-fA-F]{130}$");

    private PersonalSignVerifier() {
    }

    /**
     * @param message      the exact text that was signed (UTF-8)
     * @param signatureHex 0x + 130 hex characters: r (32 bytes), s (32 bytes), v (1 byte, 27/28 or 0/1)
     * @return the signer's address, lowercase with 0x prefix
     * @throws IllegalArgumentException if the signature is malformed or no signer can be recovered
     */
    public static String recoverAddress(String message, String signatureHex) {
        if (message == null || signatureHex == null) {
            throw new IllegalArgumentException("Message and signature are required");
        }
        String text = signatureHex.trim();
        if (!SIGNATURE.matcher(text).matches()) {
            throw new IllegalArgumentException("Signature must be 0x followed by 130 hexadecimal characters");
        }
        byte[] raw = Numeric.hexStringToByteArray(text);
        byte v = raw[64];
        if (v == 0 || v == 1) {
            v += 27; // some wallets and libraries emit the recovery id instead of 27/28
        }
        if (v != 27 && v != 28) {
            throw new IllegalArgumentException("Signature has an invalid recovery value");
        }
        Sign.SignatureData data = new Sign.SignatureData(v, Arrays.copyOfRange(raw, 0, 32), Arrays.copyOfRange(raw, 32, 64));
        try {
            BigInteger publicKey = Sign.signedPrefixedMessageToKey(message.getBytes(StandardCharsets.UTF_8), data);
            return "0x" + Keys.getAddress(publicKey).toLowerCase(Locale.ROOT);
        } catch (SignatureException | RuntimeException ex) {
            throw new IllegalArgumentException("The signer could not be recovered from this signature", ex);
        }
    }
}
