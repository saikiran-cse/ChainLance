package com.chainlance.repository;

import com.chainlance.entity.Submission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByMilestoneIdOrderBySubmittedAtDesc(Long milestoneId);

    Optional<Submission> findFirstByMilestoneIdOrderBySubmittedAtDesc(Long milestoneId);
}
