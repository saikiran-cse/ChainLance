package com.chainlance.service;

import com.chainlance.config.PricingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Converts the agreed INR price to wei with the configured DEMO rate. Rounds DOWN so we never promise more than agreed. */
@Component
public class PriceConverter {

    private static final BigDecimal WEI_PER_ETH = new BigDecimal("1000000000000000000");

    private final PricingProperties properties;

    public PriceConverter(PricingProperties properties) {
        this.properties = properties;
    }

    public BigDecimal inrPerEth() {
        return properties.inrPerEth();
    }

    /** wei = inr * 10^18 / inrPerEth, whole number. */
    public BigDecimal toWei(BigDecimal inr) {
        return inr.multiply(WEI_PER_ETH).divide(properties.inrPerEth(), 0, RoundingMode.DOWN);
    }
}
