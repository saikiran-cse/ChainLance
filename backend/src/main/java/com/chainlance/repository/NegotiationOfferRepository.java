package com.chainlance.repository;

import com.chainlance.entity.NegotiationOffer;
import com.chainlance.entity.enums.OfferStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NegotiationOfferRepository extends JpaRepository<NegotiationOffer, Long> {

    /** Oldest first; the id breaks ties when two offers share a millisecond. */
    @EntityGraph(attributePaths = {"offeredBy"})
    List<NegotiationOffer> findByProposalIdOrderByCreatedAtAscIdAsc(Long proposalId);

    Optional<NegotiationOffer> findFirstByProposalIdAndStatus(Long proposalId, OfferStatus status);

    boolean existsByProposalIdAndStatus(Long proposalId, OfferStatus status);
}
