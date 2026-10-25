package com.chainlance.chain;

import java.math.BigInteger;

public record EscrowCreatedEvent(
        String projectId,
        String escrow,
        String client,
        String freelancer,
        String admin,
        BigInteger totalAmount
) {
}
