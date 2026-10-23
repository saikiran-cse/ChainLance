package com.chainlance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import com.chainlance.entity.enums.DisputeDecision;
import com.chainlance.entity.enums.DisputeStatus;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Persistence mapping for table disputes. No business logic here.
 * Associations are LAZY many-to-one only (no collections, no cascades), so nothing is deleted or loaded implicitly.
 */
@Entity
@Table(name = "disputes")
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "reason_hash", nullable = true, length = 66, columnDefinition = "char(66)")
    private String reasonHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private DisputeStatus status = DisputeStatus.AWAITING_OPEN_TX;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "admin_decision", nullable = true, length = 30)
    private DisputeDecision adminDecision;

    @Column(name = "admin_freelancer_wei", nullable = true, precision = 38, scale = 0)
    private BigDecimal adminFreelancerWei;

    @Column(name = "admin_client_wei", nullable = true, precision = 38, scale = 0)
    private BigDecimal adminClientWei;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "admin_explanation", nullable = true, columnDefinition = "TEXT")
    private String adminExplanation;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "resolution_hash", nullable = true, length = 66, columnDefinition = "char(66)")
    private String resolutionHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "decided_by", nullable = true)
    private User decidedBy;

    @Column(name = "decided_at", nullable = true)
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "open_tx_id", nullable = true)
    private BlockchainTransaction openTx;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_tx_id", nullable = true)
    private BlockchainTransaction resolvedTx;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Dispute() {
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

    public User getOpenedBy() {
        return openedBy;
    }

    public void setOpenedBy(User openedBy) {
        this.openedBy = openedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReasonHash() {
        return reasonHash;
    }

    public void setReasonHash(String reasonHash) {
        this.reasonHash = reasonHash;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public void setStatus(DisputeStatus status) {
        this.status = status;
    }

    public DisputeDecision getAdminDecision() {
        return adminDecision;
    }

    public void setAdminDecision(DisputeDecision adminDecision) {
        this.adminDecision = adminDecision;
    }

    public BigDecimal getAdminFreelancerWei() {
        return adminFreelancerWei;
    }

    public void setAdminFreelancerWei(BigDecimal adminFreelancerWei) {
        this.adminFreelancerWei = adminFreelancerWei;
    }

    public BigDecimal getAdminClientWei() {
        return adminClientWei;
    }

    public void setAdminClientWei(BigDecimal adminClientWei) {
        this.adminClientWei = adminClientWei;
    }

    public String getAdminExplanation() {
        return adminExplanation;
    }

    public void setAdminExplanation(String adminExplanation) {
        this.adminExplanation = adminExplanation;
    }

    public String getResolutionHash() {
        return resolutionHash;
    }

    public void setResolutionHash(String resolutionHash) {
        this.resolutionHash = resolutionHash;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(User decidedBy) {
        this.decidedBy = decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public BlockchainTransaction getOpenTx() {
        return openTx;
    }

    public void setOpenTx(BlockchainTransaction openTx) {
        this.openTx = openTx;
    }

    public BlockchainTransaction getResolvedTx() {
        return resolvedTx;
    }

    public void setResolvedTx(BlockchainTransaction resolvedTx) {
        this.resolvedTx = resolvedTx;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
