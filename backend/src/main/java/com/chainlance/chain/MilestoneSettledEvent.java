package com.chainlance.chain;

import java.math.BigInteger;

public record MilestoneSettledEvent(
        int index,
        BigInteger freelancerAmount,
        BigInteger clientAmount
) {
}
