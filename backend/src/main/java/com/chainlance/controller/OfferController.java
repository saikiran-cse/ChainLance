package com.chainlance.controller;

import com.chainlance.dto.OfferRequest;
import com.chainlance.dto.OfferResponse;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.NegotiationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Both the client and the freelancer of a proposal negotiate here. The service checks who is a participant. */
@RestController
@RequestMapping("/api/proposals/{proposalId}/offers")
public class OfferController {

    private final NegotiationService negotiationService;

    public OfferController(NegotiationService negotiationService) {
        this.negotiationService = negotiationService;
    }

    @GetMapping
    public List<OfferResponse> history(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long proposalId) {
        return negotiationService.history(proposalId, current.getId());
    }

    /** A fresh offer. Allowed only when no offer is waiting for an answer. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse makeOffer(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long proposalId,
                                   @Valid @RequestBody OfferRequest request) {
        return negotiationService.makeOffer(proposalId, current.getId(), request);
    }

    /** The OTHER party accepts the pending offer. */
    @PostMapping("/{offerId}/accept")
    public OfferResponse accept(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long proposalId,
                                @PathVariable Long offerId) {
        return negotiationService.accept(proposalId, offerId, current.getId());
    }

    /** The OTHER party answers the pending offer with a new price. */
    @PostMapping("/{offerId}/counter")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse counter(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long proposalId,
                                 @PathVariable Long offerId, @Valid @RequestBody OfferRequest request) {
        return negotiationService.counter(proposalId, offerId, current.getId(), request);
    }

    /** The author takes back their own pending offer. */
    @PostMapping("/{offerId}/withdraw")
    public OfferResponse withdraw(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long proposalId,
                                  @PathVariable Long offerId) {
        return negotiationService.withdraw(proposalId, offerId, current.getId());
    }
}
