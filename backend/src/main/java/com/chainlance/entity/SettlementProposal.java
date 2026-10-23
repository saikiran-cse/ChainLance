package com.chainlance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import com.chainlance.entity.enums.SettlementStatus;
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
 * Persistence mapping for table settlement_proposals. No business logic here.
 * Associations are LAZY many-to-one only (no collections, no cascades), so nothing is deleted or loaded implicitly.
 */
@Entity
@Table(name = "settlement_proposals")
public class SettlementProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposer_id", nullable = false)
    private User proposer;

    @Column(name = "freelancer_wei", nullable = false, precision = 38, scale = 0)
    private BigDecimal freelancerWei;

    @Column(name = "client_wei", nullable = false, precision = 38, scale = 0)
    private BigDecimal clientWei;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "reason", nullable = true, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 30)
    private SettlementStatus status = SettlementStatus.AWAITING_PROPOSE_TX;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "propose_tx_id", nullable = true)
    private BlockchainTransaction proposeTx;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "executed_tx_id", nullable = true)
    private BlockchainTransaction executedTx;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at", nullable = true)
    private Instant decidedAt;

    public SettlementProposal() {
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

    public User getProposer() {
        return proposer;
    }

    public void setProposer(User proposer) {
        this.proposer = proposer;
    }

    public BigDecimal getFreelancerWei() {
        return freelancerWei;
    }

    public void setFreelancerWei(BigDecimal freelancerWei) {
        this.freelancerWei = freelancerWei;
    }

    public BigDecimal getClientWei() {
        return clientWei;
    }

    public void setClientWei(BigDecimal clientWei) {
        this.clientWei = clientWei;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public void setStatus(SettlementStatus status) {
        this.status = status;
    }

    public BlockchainTransaction getProposeTx() {
        return proposeTx;
    }

    public void setProposeTx(BlockchainTransaction proposeTx) {
        this.proposeTx = proposeTx;
    }

    public BlockchainTransaction getExecutedTx() {
        return executedTx;
    }

    public void setExecutedTx(BlockchainTransaction executedTx) {
        this.executedTx = executedTx;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
