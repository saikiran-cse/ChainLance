package com.chainlance.repository;

import com.chainlance.entity.WalletChallenge;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletChallengeRepository extends JpaRepository<WalletChallenge, Long> {

    Optional<WalletChallenge> findByNonce(String nonce);

    /** Pending (unused, unexpired) challenges for a user; used to limit how many can be open at once. */
    long countByUserIdAndUsedAtIsNullAndExpiresAtAfter(Long userId, Instant now);

    /**
     * Atomically consumes a challenge. Returns 1 only if it was unused and not expired, otherwise 0,
     * so two concurrent verifications of the same nonce can never both succeed.
     * Must be called inside a transaction.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update WalletChallenge c set c.usedAt = :now where c.id = :id and c.usedAt is null and c.expiresAt > :now")
    int markUsedIfValid(@Param("id") Long id, @Param("now") Instant now);
}
