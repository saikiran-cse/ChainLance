package com.chainlance.dto;

import com.chainlance.entity.BlockchainTransaction;
import com.chainlance.entity.enums.TransactionStatus;
import com.chainlance.entity.enums.TransactionType;
import java.time.Instant;
import java.util.Map;

public record TransactionResponse(
        Long id,
        String txHash,
        TransactionType type,
        TransactionStatus status,
        Long agreementId,
        Long milestoneId,
        String fromAddress,
        String toAddress,
        Long chainId,
        Long blockNumber,
        String valueWei,
        Map<String, Object> event,
        Instant verifiedAt,
        String error,
        Instant createdAt
) {
    public static TransactionResponse from(BlockchainTransaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getTxHash(),
                tx.getType(),
                tx.getStatus(),
                tx.getAgreement() != null ? tx.getAgreement().getId() : null,
                tx.getMilestone() != null ? tx.getMilestone().getId() : null,
                tx.getFromAddress(),
                tx.getToAddress(),
                tx.getChainId(),
                tx.getBlockNumber(),
                tx.getValueWei() != null ? tx.getValueWei().toPlainString() : null,
                tx.getEvent(),
                tx.getVerifiedAt(),
                tx.getError(),
                tx.getCreatedAt()
        );
    }
}
