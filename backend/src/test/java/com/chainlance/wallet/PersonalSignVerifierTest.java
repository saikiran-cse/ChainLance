package com.chainlance.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

/** Pure unit test. The fixed vectors were produced and cross-checked by an independent implementation. */
class PersonalSignVerifierTest {

    // Published web3.js documentation example: accounts.sign('Some data', <key>)
    private static final String DOCS_SIGNATURE =
            "0xb91467e570a6466aa9e9876cbcd013baba02900b8979d43fe208a4a4f339f5fd"
                    + "6007e74cd82e037b800186422fc2da167c747ef045e5d18a5f5d4300f8e1a0291c";
    private static final String DOCS_ADDRESS = "0x2c7536e3605d9c16a7a3d7b1898e529396a65c23";

    // A challenge-style message: several lines plus non-ASCII characters (length prefix is in BYTES).
    private static final String CHALLENGE_MESSAGE =
            "ChainLance wallet verification\n\nSign this message to prove you control the wallet below.\n"
                    + "Account ID: 42\nWallet: 0x2c7536e3605d9c16a7a3d7b1898e529396a65c23\n"
                    + "Nonce: 0123456789abcdef0123456789abcdef\n"
                    + "Issued At: 2026-10-23T10:00:00Z\nExpires At: 2026-10-23T10:05:00Z\nCaf\u00e9 \u20ac \u2713";
    private static final String CHALLENGE_SIGNATURE =
            "0xb54e6bf1c77e47aa54ef70d3edd2a5d2361ea078f79db774830b45ab06907abf"
                    + "3f7acd3975684fe9fd051828aecad60263465e5d4a05d5196509e6ac369d4e421b";
    private static final String CHALLENGE_SIGNATURE_V01 =
            "0xb54e6bf1c77e47aa54ef70d3edd2a5d2361ea078f79db774830b45ab06907abf"
                    + "3f7acd3975684fe9fd051828aecad60263465e5d4a05d5196509e6ac369d4e4200";

    @Test
    void recoversTheSignerOfAPublishedTestVector() {
        assertEquals(DOCS_ADDRESS, PersonalSignVerifier.recoverAddress("Some data", DOCS_SIGNATURE));
    }

    @Test
    void recoversTheSignerOfAMultiLineUnicodeChallenge() {
        assertEquals(DOCS_ADDRESS, PersonalSignVerifier.recoverAddress(CHALLENGE_MESSAGE, CHALLENGE_SIGNATURE));
    }

    @Test
    void acceptsRecoveryIdZeroOrOneInsteadOf27Or28() {
        assertEquals(DOCS_ADDRESS, PersonalSignVerifier.recoverAddress(CHALLENGE_MESSAGE, CHALLENGE_SIGNATURE_V01));
    }

    @Test
    void aChangedMessageDoesNotRecoverTheSameAddress() {
        String recovered = PersonalSignVerifier.recoverAddress(CHALLENGE_MESSAGE + " ", CHALLENGE_SIGNATURE);
        assertNotEquals(DOCS_ADDRESS, recovered);
    }

    @Test
    void roundTripsWithAFreshKeyUsingTheLibrarysOwnSigner() throws Exception {
        ECKeyPair keyPair = WalletTestSupport.newKeyPair();
        String signature = WalletTestSupport.personalSign(keyPair, CHALLENGE_MESSAGE);

        assertEquals(WalletTestSupport.addressOf(keyPair), PersonalSignVerifier.recoverAddress(CHALLENGE_MESSAGE, signature));
    }

    @Test
    void aSignatureFromAnotherKeyRecoversThatOtherKeysAddress() throws Exception {
        ECKeyPair signer = WalletTestSupport.newKeyPair();
        ECKeyPair other = WalletTestSupport.newKeyPair();
        String signature = WalletTestSupport.personalSign(signer, "hello");

        assertNotEquals(WalletTestSupport.addressOf(other), PersonalSignVerifier.recoverAddress("hello", signature));
    }

    @Test
    void rejectsMalformedSignatures() {
        String ok = DOCS_SIGNATURE;
        assertThrows(IllegalArgumentException.class, () -> PersonalSignVerifier.recoverAddress("m", null));
        assertThrows(IllegalArgumentException.class, () -> PersonalSignVerifier.recoverAddress(null, ok));
        assertThrows(IllegalArgumentException.class, () -> PersonalSignVerifier.recoverAddress("m", ""));
        assertThrows(IllegalArgumentException.class, () -> PersonalSignVerifier.recoverAddress("m", ok.substring(2)));
        assertThrows(IllegalArgumentException.class, () -> PersonalSignVerifier.recoverAddress("m", ok.substring(0, ok.length() - 2)));
        assertThrows(IllegalArgumentException.class, () -> PersonalSignVerifier.recoverAddress("m", ok + "00"));
        assertThrows(IllegalArgumentException.class,
                () -> PersonalSignVerifier.recoverAddress("m", "0x" + "zz".repeat(65)));
        assertThrows(IllegalArgumentException.class,
                () -> PersonalSignVerifier.recoverAddress("m", ok.substring(0, ok.length() - 2) + "1d")); // v = 29
    }
}
