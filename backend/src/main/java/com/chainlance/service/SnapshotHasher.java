package com.chainlance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

/**
 * Turns an agreement snapshot into a fingerprint: keccak256 of its canonical JSON (keys sorted, no spaces).
 * Same content always gives the same 0x + 64 hex hash, whatever the key order. Any edit changes the hash.
 * The snapshot only holds text, whole numbers, maps and lists, so the JSON text is stable.
 */
@Component
public class SnapshotHasher {

    private final ObjectMapper canonical = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    public String canonicalJson(Map<String, Object> snapshot) {
        try {
            return canonical.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the agreement snapshot", e);
        }
    }

    public String hash(Map<String, Object> snapshot) {
        byte[] digest = Hash.sha3(canonicalJson(snapshot).getBytes(StandardCharsets.UTF_8));
        return Numeric.toHexString(digest);
    }

    public boolean matches(Map<String, Object> snapshot, String expectedHash) {
        return snapshot != null && expectedHash != null && hash(snapshot).equalsIgnoreCase(expectedHash);
    }
}
