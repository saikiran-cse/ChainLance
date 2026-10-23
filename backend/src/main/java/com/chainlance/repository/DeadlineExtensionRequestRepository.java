package com.chainlance.repository;

import com.chainlance.entity.DeadlineExtensionRequest;
import com.chainlance.entity.enums.ExtensionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadlineExtensionRequestRepository extends JpaRepository<DeadlineExtensionRequest, Long> {

    List<DeadlineExtensionRequest> findByMilestoneIdOrderByRequestedAtDesc(Long milestoneId);

    /** At most one PENDING request per milestone is guaranteed by a unique index in the database. */
    Optional<DeadlineExtensionRequest> findByMilestoneIdAndStatus(Long milestoneId, ExtensionStatus status);
}
