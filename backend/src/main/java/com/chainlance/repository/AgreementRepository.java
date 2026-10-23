package com.chainlance.repository;

import com.chainlance.entity.Agreement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgreementRepository extends JpaRepository<Agreement, Long> {

    Optional<Agreement> findByProposalId(Long proposalId);

    Optional<Agreement> findByEscrowAddress(String escrowAddress);

    Optional<Agreement> findByOnchainProjectId(String onchainProjectId);

    /** Agreements where the user is the project's client or the proposal's freelancer. Fetches the chain in one query. */
    @Query("select a from Agreement a join fetch a.proposal p join fetch p.project pr "
            + "where p.freelancer.id = :userId or pr.client.id = :userId order by a.createdAt desc")
    List<Agreement> findAllForParticipant(@Param("userId") Long userId);
}
