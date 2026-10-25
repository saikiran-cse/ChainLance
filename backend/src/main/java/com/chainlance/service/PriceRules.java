package com.chainlance.service;

import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;

/** Small shared checks for money and duration. Money is always BigDecimal with exactly 2 decimals (INR). */
final class PriceRules {

    static final int MIN_DAYS = 1;
    static final int MAX_DAYS = 3650;

    private PriceRules() {
    }

    /** Rejects zero, negative and more-than-2-decimal prices, and returns the value with scale 2. */
    static BigDecimal normalize(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "Price must be greater than zero");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "Price can have at most 2 decimal places");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** The project's budget is the ceiling for every proposal and offer. */
    static void requireWithinBudget(BigDecimal price, BigDecimal budget) {
        if (price.compareTo(budget) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICE_EXCEEDS_BUDGET",
                    "Price cannot be higher than the project budget of " + budget.toPlainString() + " INR");
        }
    }

    static int requireDuration(Integer days) {
        if (days == null || days < MIN_DAYS || days > MAX_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DURATION",
                    "Duration must be between " + MIN_DAYS + " and " + MAX_DAYS + " days");
        }
        return days;
    }

    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
