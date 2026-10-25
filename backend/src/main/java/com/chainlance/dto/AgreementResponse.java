package com.chainlance.dto;

import com.chainlance.entity.Agreement;
import com.chainlance.entity.Project;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.enums.AgreementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Read-only view of an agreement. totalWei is a String so JavaScript cannot round a 19+ digit number.
 * snapshotIntegrityOk is recomputed on every read: false would mean the stored snapshot no longer matches its hash.
 */
public record AgreementResponse(Long id, Long proposalId, Long projectId, PublicUserResponse client,
                                PublicUserResponse freelancer, AgreementStatus status, BigDecimal priceInr,
                                String totalWei, Map<String, Object> snapshot, String snapshotHash,
                                boolean snapshotIntegrityOk, String clientWallet, String freelancerWallet,
                                String onchainProjectId, String factoryAddress, String escrowAddress, Long chainId,
                                Instant clientFinalizedAt, Instant freelancerFinalizedAt, Instant createdAt) {

    public static AgreementResponse from(Agreement a, boolean integrityOk) {
        Proposal proposal = a.getProposal();
        Project project = proposal.getProject();
        return new AgreementResponse(a.getId(), proposal.getId(), project.getId(),
                PublicUserResponse.from(project.getClient()), PublicUserResponse.from(proposal.getFreelancer()),
                a.getStatus(), priceFromSnapshot(a.getSnapshot()), a.getTotalWei().toBigInteger().toString(),
                a.getSnapshot(), a.getSnapshotHash(), integrityOk, a.getClientWallet(), a.getFreelancerWallet(),
                a.getOnchainProjectId(), a.getFactoryAddress(), a.getEscrowAddress(), a.getChainId(),
                a.getClientFinalizedAt(), a.getFreelancerFinalizedAt(), a.getCreatedAt());
    }

    private static BigDecimal priceFromSnapshot(Map<String, Object> snapshot) {
        if (snapshot != null && snapshot.get("agreedTerms") instanceof Map<?, ?> terms && terms.get("priceInr") != null) {
            return new BigDecimal(terms.get("priceInr").toString());
        }
        return null;
    }
}
