package com.chainlance.repository;

import com.chainlance.entity.ProjectMilestonePlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMilestonePlanRepository extends JpaRepository<ProjectMilestonePlan, Long> {

    List<ProjectMilestonePlan> findByProjectIdOrderByPositionAsc(Long projectId);

    void deleteByProjectId(Long projectId);

    long countByProjectId(Long projectId);
}
