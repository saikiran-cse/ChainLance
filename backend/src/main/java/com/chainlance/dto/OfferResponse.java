package com.chainlance.dto;

import com.chainlance.entity.NegotiationOffer;
import com.chainlance.entity.enums.OfferStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OfferResponse(Long id, Long proposalId, PublicUserResponse offeredBy, BigDecimal priceInr,
                            int durationDays, String note, OfferStatus status, Long respondedBy, Instant respondedAt,
                            Instant createdAt) {

    public static OfferResponse from(NegotiationOffer o) {
        return new OfferResponse(o.getId(), o.getProposal().getId(), PublicUserResponse.from(o.getOfferedBy()),
                o.getPriceInr(), o.getDurationDays(), o.getNote(), o.getStatus(),
                o.getRespondedBy() == null ? null : o.getRespondedBy().getId(), o.getRespondedAt(), o.getCreatedAt());
    }
}
