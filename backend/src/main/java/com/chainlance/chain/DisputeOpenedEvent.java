package com.chainlance.chain;

public record DisputeOpenedEvent(
        int index,
        String openedBy,
        String reasonHash
) {
}
