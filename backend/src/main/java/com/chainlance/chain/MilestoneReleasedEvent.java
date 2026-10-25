package com.chainlance.chain;

import java.math.BigInteger;

public record MilestoneReleasedEvent(
        int index,
        String freelancer,
        BigInteger amount
) {
}
