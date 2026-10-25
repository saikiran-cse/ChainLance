package com.chainlance.chain;

import java.math.BigInteger;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

public final class OnchainProjectId {

    private OnchainProjectId() {
    }

    /**
     * Matches ethers.solidityPackedKeccak256(["uint256", "bytes32"], [agreementId, snapshotHash]).
     * 32-byte big-endian id followed by the 32-byte snapshot hash, hashed with Keccak-256.
     */
    public static String compute(Long agreementId, String snapshotHash) {
        if (agreementId == null) {
            throw new IllegalArgumentException("agreementId is required");
        }
        if (snapshotHash == null || snapshotHash.isBlank()) {
            throw new IllegalArgumentException("snapshotHash is required");
        }
        byte[] idBytes = Numeric.toBytesPadded(BigInteger.valueOf(agreementId), 32);
        byte[] hashBytes = Numeric.hexStringToByteArray(snapshotHash);
        if (hashBytes.length != 32) {
            throw new IllegalArgumentException("snapshotHash must be 32 bytes (64 hex characters, with optional 0x prefix)");
        }
        byte[] combined = new byte[64];
        System.arraycopy(idBytes, 0, combined, 0, 32);
        System.arraycopy(hashBytes, 0, combined, 32, 32);
        return Numeric.toHexString(Hash.sha3(combined));
    }
}
