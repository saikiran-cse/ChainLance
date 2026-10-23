package com.chainlance.repository;

import com.chainlance.entity.NegotiationMessage;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NegotiationMessageRepository extends JpaRepository<NegotiationMessage, Long> {

    @EntityGraph(attributePaths = {"sender"})
    List<NegotiationMessage> findByProposalIdOrderByCreatedAtAsc(Long proposalId);
}
