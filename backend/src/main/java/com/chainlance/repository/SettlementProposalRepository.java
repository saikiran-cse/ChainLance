package com.chainlance.repository;

import com.chainlance.entity.SettlementProposal;
import com.chainlance.entity.enums.SettlementStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementProposalRepository extends JpaRepository<SettlementProposal, Long> {

    List<SettlementProposal> findByMilestoneIdOrderByCreatedAtDesc(Long milestoneId);

    /** Pass AWAITING_PROPOSE_TX and ACTIVE to find the live proposal; the database allows at most one. */
    Optional<SettlementProposal> findByMilestoneIdAndStatusIn(Long milestoneId, Collection<SettlementStatus> statuses);
}
