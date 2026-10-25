package com.chainlance.chain;

import java.math.BigInteger;

public record FundedEvent(
        String client,
        BigInteger amount
) {
}
