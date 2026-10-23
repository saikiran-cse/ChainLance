package com.chainlance.wallet;

import java.nio.charset.StandardCharsets;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/** Test-only helpers: a throwaway key pair and a MetaMask-style personal_sign. Never use real keys in tests. */
public final class WalletTestSupport {

    private WalletTestSupport() {
    }

    public static ECKeyPair newKeyPair() throws Exception {
        return Keys.createEcKeyPair();
    }

    /** Lowercase 0x address of the key pair. */
    public static String addressOf(ECKeyPair keyPair) {
        return "0x" + Keys.getAddress(keyPair.getPublicKey()).toLowerCase();
    }

    /** Signs like MetaMask's signMessage: EIP-191 prefix, returns 0x + r + s + v (65 bytes). */
    public static String personalSign(ECKeyPair keyPair, String message) {
        Sign.SignatureData data = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), keyPair);
        byte[] out = new byte[65];
        System.arraycopy(data.getR(), 0, out, 0, 32);
        System.arraycopy(data.getS(), 0, out, 32, 32);
        Object v = data.getV(); // byte[] in recent web3j versions, byte in older ones
        out[64] = v instanceof byte[] array ? array[0] : (Byte) v;
        return Numeric.toHexString(out);
    }
}
