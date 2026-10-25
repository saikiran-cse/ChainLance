package com.chainlance.repository;

import com.chainlance.entity.Project;
import com.chainlance.entity.enums.ProjectStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @EntityGraph(attributePaths = {"client"})
    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"client"})
    Page<Project> findByStatusAndCategoryIgnoreCase(ProjectStatus status, String category, Pageable pageable);

    List<Project> findByClientIdOrderByCreatedAtDesc(Long clientId);
}
