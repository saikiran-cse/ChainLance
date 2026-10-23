package com.chainlance.repository;

import com.chainlance.entity.Proposal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    Optional<Proposal> findByProjectIdAndFreelancerId(Long projectId, Long freelancerId);

    boolean existsByProjectIdAndFreelancerId(Long projectId, Long freelancerId);

    @EntityGraph(attributePaths = {"freelancer"})
    List<Proposal> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    @EntityGraph(attributePaths = {"project"})
    List<Proposal> findByFreelancerIdOrderByCreatedAtDesc(Long freelancerId);
}
