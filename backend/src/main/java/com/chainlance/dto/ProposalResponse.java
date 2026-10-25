package com.chainlance.dto;

import com.chainlance.entity.Proposal;
import com.chainlance.entity.enums.ProposalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * priceInr and durationDays are the freelancer's ORIGINAL proposal. The negotiated numbers live in the offers list,
 * which is filled only on the detail view (it is omitted from the JSON when null).
 */
public record ProposalResponse(Long id, Long projectId, String projectTitle, PublicUserResponse freelancer,
                               String message, BigDecimal priceInr, int durationDays, String portfolioLinks,
                               ProposalStatus status, Instant createdAt, Instant updatedAt,
                               List<OfferResponse> offers) {

    public static ProposalResponse from(Proposal p, List<OfferResponse> offers) {
        return new ProposalResponse(p.getId(), p.getProject().getId(), p.getProject().getTitle(),
                PublicUserResponse.from(p.getFreelancer()), p.getMessage(), p.getPriceInr(), p.getDurationDays(),
                p.getPortfolioLinks(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt(), offers);
    }
}
