package com.chainlance.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import com.chainlance.entity.enums.AgreementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Persistence mapping for table agreements. No business logic here.
 * Associations are LAZY many-to-one only (no collections, no cascades), so nothing is deleted or loaded implicitly.
 */
@Entity
@Table(name = "agreements")
public class Agreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private Proposal proposal;

    @Column(name = "total_wei", nullable = false, precision = 38, scale = 0)
    private BigDecimal totalWei;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private AgreementStatus status = AgreementStatus.DRAFT;

    @Column(name = "client_finalized_at", nullable = true)
    private Instant clientFinalizedAt;

    @Column(name = "freelancer_finalized_at", nullable = true)
    private Instant freelancerFinalizedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", columnDefinition = "json")
    private Map<String, Object> snapshot;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "snapshot_hash", nullable = true, length = 66, columnDefinition = "char(66)")
    private String snapshotHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "client_wallet", nullable = true, length = 42, columnDefinition = "char(42)")
    private String clientWallet;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "freelancer_wallet", nullable = true, length = 42, columnDefinition = "char(42)")
    private String freelancerWallet;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "onchain_project_id", nullable = true, length = 66, columnDefinition = "char(66)")
    private String onchainProjectId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "factory_address", nullable = true, length = 42, columnDefinition = "char(42)")
    private String factoryAddress;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "escrow_address", nullable = true, length = 42, columnDefinition = "char(42)")
    private String escrowAddress;

    @Column(name = "chain_id", nullable = true)
    private Long chainId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Agreement() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public void setProposal(Proposal proposal) {
        this.proposal = proposal;
    }

    public BigDecimal getTotalWei() {
        return totalWei;
    }

    public void setTotalWei(BigDecimal totalWei) {
        this.totalWei = totalWei;
    }

    public AgreementStatus getStatus() {
        return status;
    }

    public void setStatus(AgreementStatus status) {
        this.status = status;
    }

    public Instant getClientFinalizedAt() {
        return clientFinalizedAt;
    }

    public void setClientFinalizedAt(Instant clientFinalizedAt) {
        this.clientFinalizedAt = clientFinalizedAt;
    }

    public Instant getFreelancerFinalizedAt() {
        return freelancerFinalizedAt;
    }

    public void setFreelancerFinalizedAt(Instant freelancerFinalizedAt) {
        this.freelancerFinalizedAt = freelancerFinalizedAt;
    }

    public Map<String, Object> getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Map<String, Object> snapshot) {
        this.snapshot = snapshot;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public void setSnapshotHash(String snapshotHash) {
        this.snapshotHash = snapshotHash;
    }

    public String getClientWallet() {
        return clientWallet;
    }

    public void setClientWallet(String clientWallet) {
        this.clientWallet = clientWallet;
    }

    public String getFreelancerWallet() {
        return freelancerWallet;
    }

    public void setFreelancerWallet(String freelancerWallet) {
        this.freelancerWallet = freelancerWallet;
    }

    public String getOnchainProjectId() {
        return onchainProjectId;
    }

    public void setOnchainProjectId(String onchainProjectId) {
        this.onchainProjectId = onchainProjectId;
    }

    public String getFactoryAddress() {
        return factoryAddress;
    }

    public void setFactoryAddress(String factoryAddress) {
        this.factoryAddress = factoryAddress;
    }

    public String getEscrowAddress() {
        return escrowAddress;
    }

    public void setEscrowAddress(String escrowAddress) {
        this.escrowAddress = escrowAddress;
    }

    public Long getChainId() {
        return chainId;
    }

    public void setChainId(Long chainId) {
        this.chainId = chainId;
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
