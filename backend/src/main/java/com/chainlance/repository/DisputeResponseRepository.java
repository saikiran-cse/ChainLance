package com.chainlance.repository;

import com.chainlance.entity.DisputeResponse;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisputeResponseRepository extends JpaRepository<DisputeResponse, Long> {

    @EntityGraph(attributePaths = {"user"})
    List<DisputeResponse> findByDisputeIdOrderByCreatedAtAsc(Long disputeId);
}
