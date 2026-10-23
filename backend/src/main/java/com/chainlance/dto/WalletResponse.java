package com.chainlance.dto;

import com.chainlance.entity.WalletAddress;
import java.time.Instant;

public record WalletResponse(Long id, String address, Instant verifiedAt) {

    public static WalletResponse from(WalletAddress wallet) {
        return new WalletResponse(wallet.getId(), wallet.getAddress(), wallet.getVerifiedAt());
    }
}
