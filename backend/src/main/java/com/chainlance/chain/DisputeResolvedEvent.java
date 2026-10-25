package com.chainlance.chain;

import java.math.BigInteger;

public record DisputeResolvedEvent(
        int index,
        String admin,
        BigInteger freelancerAmount,
        BigInteger clientAmount,
        String resolutionHash
) {
}
