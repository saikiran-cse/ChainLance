package com.chainlance.dto;

import com.chainlance.entity.Project;
import com.chainlance.entity.enums.ProjectStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ProjectResponse(Long id, PublicUserResponse client, String title, String description, String category,
                              String skills, BigDecimal budgetInr, Instant deadline, ProjectStatus status,
                              Instant createdAt, Instant updatedAt) {

    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), PublicUserResponse.from(p.getClient()), p.getTitle(), p.getDescription(),
                p.getCategory(), p.getSkills(), p.getBudgetInr(), p.getDeadline(), p.getStatus(), p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
