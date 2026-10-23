package com.chainlance.repository;

import com.chainlance.entity.Dispute;
import com.chainlance.entity.enums.DisputeStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    List<Dispute> findByMilestoneIdOrderByCreatedAtDesc(Long milestoneId);

    /** Pass AWAITING_OPEN_TX, OPEN and DECIDED to find the live dispute; the database allows at most one. */
    Optional<Dispute> findByMilestoneIdAndStatusIn(Long milestoneId, Collection<DisputeStatus> statuses);

    @EntityGraph(attributePaths = {"milestone", "openedBy"})
    Page<Dispute> findByStatusIn(Collection<DisputeStatus> statuses, Pageable pageable);

    /** Disputes on agreements where the user is the client or the freelancer. */
    @Query("select d from Dispute d join fetch d.milestone m join fetch m.agreement a join fetch a.proposal p "
            + "join fetch p.project pr where p.freelancer.id = :userId or pr.client.id = :userId order by d.createdAt desc")
    List<Dispute> findAllForParticipant(@Param("userId") Long userId);
}
