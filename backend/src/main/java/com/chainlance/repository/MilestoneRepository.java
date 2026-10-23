package com.chainlance.repository;

import com.chainlance.entity.Milestone;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    List<Milestone> findByAgreementIdOrderByPositionAsc(Long agreementId);

    /** Position is 1-based; the contract index is position - 1. */
    Optional<Milestone> findByAgreementIdAndPosition(Long agreementId, int position);
}
