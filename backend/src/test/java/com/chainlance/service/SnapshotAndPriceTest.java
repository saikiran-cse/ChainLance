package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chainlance.config.PricingProperties;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Snapshot hashing, INR to wei conversion and the money rules. No Spring, no database. */
class SnapshotAndPriceTest {

    private final SnapshotHasher hasher = new SnapshotHasher();

    // ------------------------------------------------------------------ snapshot hash

    @Test
    void sameContentInADifferentKeyOrderGivesTheSameHash() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("priceInr", "55000.00");
        a.put("terms", Map.of("x", "1", "y", "2"));
        a.put("history", List.of(Map.of("id", 1, "status", "ACCEPTED")));

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("history", List.of(Map.of("status", "ACCEPTED", "id", 1)));
        b.put("terms", Map.of("y", "2", "x", "1"));
        b.put("priceInr", "55000.00");

        assertEquals(hasher.canonicalJson(a), hasher.canonicalJson(b));
        assertEquals(hasher.hash(a), hasher.hash(b));
    }

    @Test
    void anyChangedValueChangesTheHash() {
        Map<String, Object> original = Map.of("priceInr", "55000.00");
        Map<String, Object> edited = Map.of("priceInr", "55000.01");
        assertNotEquals(hasher.hash(original), hasher.hash(edited));
    }

    @Test
    void hashIsAKeccak256StyleBytes32HexString() {
        assertTrue(hasher.hash(Map.of("k", "v")).matches("0x[0-9a-f]{64}"));
    }

    @Test
    void matchesDetectsTampering() {
        Map<String, Object> snapshot = new LinkedHashMap<>(Map.of("priceInr", "55000.00"));
        String hash = hasher.hash(snapshot);
        assertTrue(hasher.matches(snapshot, hash));
        snapshot.put("priceInr", "1.00");
        assertFalse(hasher.matches(snapshot, hash));
        assertFalse(hasher.matches(null, hash));
        assertFalse(hasher.matches(snapshot, null));
    }

    // ------------------------------------------------------------------ INR to wei

    @Test
    void convertsInrToWeiExactlyWhenTheDivisionIsExact() {
        PriceConverter converter = new PriceConverter(new PricingProperties(new BigDecimal("250000")));
        // 55,000 INR / 250,000 = 0.22 ETH
        assertEquals(new BigDecimal("220000000000000000"), converter.toWei(new BigDecimal("55000.00")));
        assertEquals(new BigDecimal("4000000000000"), converter.toWei(new BigDecimal("1.00")));
    }

    @Test
    void roundsDownWhenTheDivisionIsNotExact() {
        PriceConverter converter = new PriceConverter(new PricingProperties(new BigDecimal("300000")));
        // 100 / 300000 ETH = 333333333333333.33... wei, and we keep 333333333333333
        assertEquals(new BigDecimal("333333333333333"), converter.toWei(new BigDecimal("100.00")));
    }

    @Test
    void missingOrInvalidRateFallsBackToTheDemoDefault() {
        assertEquals(0, new BigDecimal("250000").compareTo(new PricingProperties(null).inrPerEth()));
        assertEquals(0, new BigDecimal("250000").compareTo(new PricingProperties(BigDecimal.ZERO).inrPerEth()));
    }

    // ------------------------------------------------------------------ money rules

    @Test
    void normalizeKeepsExactlyTwoDecimals() {
        assertEquals("50000.00", PriceRules.normalize(new BigDecimal("50000")).toPlainString());
        assertEquals("49.90", PriceRules.normalize(new BigDecimal("49.9")).toPlainString());
        assertEquals("100.50", PriceRules.normalize(new BigDecimal("100.500")).toPlainString());
    }

    @Test
    void normalizeRejectsZeroNegativeAndTooManyDecimals() {
        for (String bad : new String[]{"0", "-5", "0.00", "100.005"}) {
            ApiException e = assertThrows(ApiException.class, () -> PriceRules.normalize(new BigDecimal(bad)), bad);
            assertEquals("INVALID_PRICE", e.getCode());
        }
        assertThrows(ApiException.class, () -> PriceRules.normalize(null));
    }

    @Test
    void budgetIsAHardCeiling() {
        BigDecimal budget = new BigDecimal("60000.00");
        PriceRules.requireWithinBudget(new BigDecimal("60000.00"), budget); // equal is fine
        ApiException e = assertThrows(ApiException.class,
                () -> PriceRules.requireWithinBudget(new BigDecimal("60000.01"), budget));
        assertEquals("PRICE_EXCEEDS_BUDGET", e.getCode());
    }

    @Test
    void durationMustBeBetweenOneDayAndTenYears() {
        assertEquals(30, PriceRules.requireDuration(30));
        for (Integer bad : new Integer[]{null, 0, -1, 3651}) {
            ApiException e = assertThrows(ApiException.class, () -> PriceRules.requireDuration(bad));
            assertEquals("INVALID_DURATION", e.getCode());
        }
    }
}
