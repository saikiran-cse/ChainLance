package com.chainlance.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OnchainProjectIdTest {

    @Test
    void matchesHardhatHardeningVector() {
        // Exact vector from test/ChainLanceEscrow.hardening.test.js lines 81-85:
        // snapshotHash = 0x411bc73d9ea3b5244542429d99a38608950885df0f5fec423c44bbe7e84cc6c4
        // agreementId = 42
        // expected = 0x1faee1570066771b6227a97d8cc3c551e64611a51ef9d899a0044049f61fc380
        String snapshotHash = "0x411bc73d9ea3b5244542429d99a38608950885df0f5fec423c44bbe7e84cc6c4";
        Long agreementId = 42L;
        String expected = "0x1faee1570066771b6227a97d8cc3c551e64611a51ef9d899a0044049f61fc380";

        String computed = OnchainProjectId.compute(agreementId, snapshotHash);
        assertEquals(expected.toLowerCase(), computed.toLowerCase());
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> OnchainProjectId.compute(null, "0x1234"));
        assertThrows(IllegalArgumentException.class, () -> OnchainProjectId.compute(1L, null));
        assertThrows(IllegalArgumentException.class, () -> OnchainProjectId.compute(1L, "0xshort"));
    }
}
