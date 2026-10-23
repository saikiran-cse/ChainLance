package com.chainlance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import com.chainlance.entity.enums.TransactionStatus;
import com.chainlance.entity.enums.TransactionType;
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
 * Persistence mapping for table blockchain_transactions. No business logic here.
 * Associations are LAZY many-to-one only (no collections, no cascades), so nothing is deleted or loaded implicitly.
 */
@Entity
@Table(name = "blockchain_transactions")
public class BlockchainTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "tx_hash", nullable = false, length = 66, columnDefinition = "char(66)")
    private String txHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "agreement_id", nullable = true)
    private Agreement agreement;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "milestone_id", nullable = true)
    private Milestone milestone;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "submitted_by", nullable = true)
    private User submittedBy;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "from_address", nullable = true, length = 42, columnDefinition = "char(42)")
    private String fromAddress;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "to_address", nullable = true, length = 42, columnDefinition = "char(42)")
    private String toAddress;

    @Column(name = "chain_id", nullable = false)
    private Long chainId;

    @Column(name = "block_number", nullable = true)
    private Long blockNumber;

    @Column(name = "value_wei", nullable = true, precision = 38, scale = 0)
    private BigDecimal valueWei;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_json", columnDefinition = "json")
    private Map<String, Object> event;

    @Column(name = "verified_at", nullable = true)
    private Instant verifiedAt;

    @Column(name = "error", nullable = true, length = 1000)
    private String error;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BlockchainTransaction() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public String getTxHash() {
        return txHash;
    }

    public void setTxHash(String txHash) {
        this.txHash = txHash;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public Agreement getAgreement() {
        return agreement;
    }

    public void setAgreement(Agreement agreement) {
        this.agreement = agreement;
    }

    public Milestone getMilestone() {
        return milestone;
    }

    public void setMilestone(Milestone milestone) {
        this.milestone = milestone;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(User submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public Long getChainId() {
        return chainId;
    }

    public void setChainId(Long chainId) {
        this.chainId = chainId;
    }

    public Long getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(Long blockNumber) {
        this.blockNumber = blockNumber;
    }

    public BigDecimal getValueWei() {
        return valueWei;
    }

    public void setValueWei(BigDecimal valueWei) {
        this.valueWei = valueWei;
    }

    public Map<String, Object> getEvent() {
        return event;
    }

    public void setEvent(Map<String, Object> event) {
        this.event = event;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
