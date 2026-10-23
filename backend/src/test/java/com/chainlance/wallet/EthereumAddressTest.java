package com.chainlance.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Pure unit test. The checksummed addresses are the official EIP-55 test vectors. */
class EthereumAddressTest {

    private static final String[] EIP55 = {
            "0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed",
            "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359",
            "0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB",
            "0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb"
    };

    @Test
    void acceptsValidChecksummedAddressesAndLowercasesThem() {
        for (String address : EIP55) {
            assertEquals(address.toLowerCase(), EthereumAddress.normalize(address));
        }
    }

    @Test
    void acceptsAllLowercaseAndAllUppercaseHexWhichCarryNoChecksum() {
        String lower = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed";
        assertEquals(lower, EthereumAddress.normalize(lower));
        assertEquals(lower, EthereumAddress.normalize("0x5AAEB6053F3E94C9B9A09F33669435E7EF1BEAED"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed",
                EthereumAddress.normalize("  0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed \n"));
    }

    @Test
    void rejectsAMixedCaseAddressWithAWrongChecksum() {
        // first hex letter after 0x changed from 'a' to 'A'
        assertThrows(IllegalArgumentException.class,
                () -> EthereumAddress.normalize("0x5AAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"));
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> EthereumAddress.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> EthereumAddress.normalize(""));
        assertThrows(IllegalArgumentException.class, () -> EthereumAddress.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> EthereumAddress.normalize("5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"));
        assertThrows(IllegalArgumentException.class, () -> EthereumAddress.normalize("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beae"));
        assertThrows(IllegalArgumentException.class, () -> EthereumAddress.normalize("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaedd"));
        assertThrows(IllegalArgumentException.class, () -> EthereumAddress.normalize("0xzzaeb6053f3e94c9b9a09f33669435e7ef1beaed"));
        assertThrows(IllegalArgumentException.class, () -> EthereumAddress.normalize("0X5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"));
    }

    @Test
    void rejectsTheZeroAddress() {
        assertThrows(IllegalArgumentException.class, () -> EthereumAddress.normalize("0x" + "0".repeat(40)));
    }
}
