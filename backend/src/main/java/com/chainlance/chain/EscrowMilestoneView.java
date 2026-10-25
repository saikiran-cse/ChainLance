package com.chainlance.chain;

import java.math.BigInteger;

public record EscrowMilestoneView(
        BigInteger allocation,
        int status, // 0: Open, 1: Released, 2: Settled, 3: Disputed, 4: DisputeResolved
        BigInteger freelancerPaid,
        BigInteger clientRefunded,
        String disputedBy
) {
}
