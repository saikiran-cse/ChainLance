package com.chainlance.repository;

import com.chainlance.entity.Proposal;
import com.chainlance.entity.enums.ProposalStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    /**
     * A freelancer may now have several proposals for one project over time (after a rejection or withdrawal),
     * so this returns a list. Only one of them can be active (see uq_proposal_one_active in V2).
     */
    List<Proposal> findByProjectIdAndFreelancerIdOrderByCreatedAtDesc(Long projectId, Long freelancerId);

    boolean existsByProjectIdAndFreelancerId(Long projectId, Long freelancerId);

    boolean existsByProjectIdAndFreelancerIdAndStatusIn(Long projectId, Long freelancerId, Collection<ProposalStatus> statuses);

    boolean existsByProjectIdAndStatusIn(Long projectId, Collection<ProposalStatus> statuses);

    List<Proposal> findByProjectIdAndStatusIn(Long projectId, Collection<ProposalStatus> statuses);

    /** Row lock (SELECT ... FOR UPDATE) so two requests on the same proposal run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proposal p where p.id = :id")
    Optional<Proposal> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"freelancer"})
    List<Proposal> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    @EntityGraph(attributePaths = {"project"})
    List<Proposal> findByFreelancerIdOrderByCreatedAtDesc(Long freelancerId);
}
