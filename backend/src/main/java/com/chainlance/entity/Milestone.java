package com.chainlance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Persistence mapping for table milestones. No business logic here.
 * Associations are LAZY many-to-one only (no collections, no cascades), so nothing is deleted or loaded implicitly.
 */
@Entity
@Table(name = "milestones")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_id", nullable = false)
    private Agreement agreement;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "acceptance_criteria", nullable = true, columnDefinition = "TEXT")
    private String acceptanceCriteria;

    @Column(name = "amount_wei", nullable = false, precision = 38, scale = 0)
    private BigDecimal amountWei;

    @Column(name = "original_deadline", nullable = true)
    private Instant originalDeadline;

    @Column(name = "effective_deadline", nullable = true)
    private Instant effectiveDeadline;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "work_status", nullable = false, length = 30)
    private MilestoneWorkStatus workStatus = MilestoneWorkStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "chain_status", nullable = false, length = 30)
    private MilestoneChainStatus chainStatus = MilestoneChainStatus.OPEN;

    @Column(name = "freelancer_paid_wei", nullable = false, precision = 38, scale = 0)
    private BigDecimal freelancerPaidWei = BigDecimal.ZERO;

    @Column(name = "client_refunded_wei", nullable = false, precision = 38, scale = 0)
    private BigDecimal clientRefundedWei = BigDecimal.ZERO;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Milestone() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public Agreement getAgreement() {
        return agreement;
    }

    public void setAgreement(Agreement agreement) {
        this.agreement = agreement;
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

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(String acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public BigDecimal getAmountWei() {
        return amountWei;
    }

    public void setAmountWei(BigDecimal amountWei) {
        this.amountWei = amountWei;
    }

    public Instant getOriginalDeadline() {
        return originalDeadline;
    }

    public void setOriginalDeadline(Instant originalDeadline) {
        this.originalDeadline = originalDeadline;
    }

    public Instant getEffectiveDeadline() {
        return effectiveDeadline;
    }

    public void setEffectiveDeadline(Instant effectiveDeadline) {
        this.effectiveDeadline = effectiveDeadline;
    }

    public MilestoneWorkStatus getWorkStatus() {
        return workStatus;
    }

    public void setWorkStatus(MilestoneWorkStatus workStatus) {
        this.workStatus = workStatus;
    }

    public MilestoneChainStatus getChainStatus() {
        return chainStatus;
    }

    public void setChainStatus(MilestoneChainStatus chainStatus) {
        this.chainStatus = chainStatus;
    }

    public BigDecimal getFreelancerPaidWei() {
        return freelancerPaidWei;
    }

    public void setFreelancerPaidWei(BigDecimal freelancerPaidWei) {
        this.freelancerPaidWei = freelancerPaidWei;
    }

    public BigDecimal getClientRefundedWei() {
        return clientRefundedWei;
    }

    public void setClientRefundedWei(BigDecimal clientRefundedWei) {
        this.clientRefundedWei = clientRefundedWei;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
