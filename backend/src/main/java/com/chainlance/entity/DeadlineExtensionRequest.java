package com.chainlance.entity;

import java.time.Instant;
import com.chainlance.entity.enums.ExtensionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persistence mapping for table deadline_extension_requests. No business logic here.
 * Associations are LAZY many-to-one only (no collections, no cascades), so nothing is deleted or loaded implicitly.
 */
@Entity
@Table(name = "deadline_extension_requests")
public class DeadlineExtensionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "current_deadline", nullable = false)
    private Instant currentDeadline;

    @Column(name = "requested_deadline", nullable = false)
    private Instant requestedDeadline;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ExtensionStatus status = ExtensionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "decided_by", nullable = true)
    private User decidedBy;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "decision_note", nullable = true, columnDefinition = "TEXT")
    private String decisionNote;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "decided_at", nullable = true)
    private Instant decidedAt;

    public DeadlineExtensionRequest() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public Milestone getMilestone() {
        return milestone;
    }

    public void setMilestone(Milestone milestone) {
        this.milestone = milestone;
    }

    public User getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(User requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getCurrentDeadline() {
        return currentDeadline;
    }

    public void setCurrentDeadline(Instant currentDeadline) {
        this.currentDeadline = currentDeadline;
    }

    public Instant getRequestedDeadline() {
        return requestedDeadline;
    }

    public void setRequestedDeadline(Instant requestedDeadline) {
        this.requestedDeadline = requestedDeadline;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public ExtensionStatus getStatus() {
        return status;
    }

    public void setStatus(ExtensionStatus status) {
        this.status = status;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(User decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
