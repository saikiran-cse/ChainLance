package com.chainlance.entity;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Persistence mapping for table project_milestone_plans. No business logic here.
 * Associations are LAZY many-to-one only (no collections, no cascades), so nothing is deleted or loaded implicitly.
 */
@Entity
@Table(name = "project_milestone_plans")
public class ProjectMilestonePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "allocation_inr", nullable = false, precision = 15, scale = 2)
    private BigDecimal allocationInr;

    public ProjectMilestonePlan() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAllocationInr() {
        return allocationInr;
    }

    public void setAllocationInr(BigDecimal allocationInr) {
        this.allocationInr = allocationInr;
    }
}
