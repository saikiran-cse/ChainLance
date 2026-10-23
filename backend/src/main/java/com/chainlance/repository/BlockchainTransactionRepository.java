package com.chainlance.repository;

import com.chainlance.entity.BlockchainTransaction;
import com.chainlance.entity.enums.TransactionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, Long> {

    /** Hashes are stored lowercase; the unique index on tx_hash makes verification idempotent. */
    Optional<BlockchainTransaction> findByTxHash(String txHash);

    boolean existsByTxHash(String txHash);

    List<BlockchainTransaction> findBySubmittedByIdOrderByCreatedAtDesc(Long userId);

    List<BlockchainTransaction> findByAgreementIdOrderByCreatedAtDesc(Long agreementId);

    List<BlockchainTransaction> findByStatus(TransactionStatus status);
}
