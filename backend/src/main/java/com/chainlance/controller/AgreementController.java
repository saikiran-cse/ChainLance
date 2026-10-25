package com.chainlance.controller;

import com.chainlance.dto.AgreementResponse;
import com.chainlance.dto.LinkWalletRequest;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.AgreementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Agreements are created by accepting a proposal and are read-only afterwards: there is no PUT, PATCH or DELETE. */
@RestController
@RequestMapping("/api")
public class AgreementController {

    private final AgreementService agreementService;

    public AgreementController(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    /**
     * Client only. Accepts the proposal AND creates the agreement snapshot in one transaction.
     * Works only after one party accepted the other's offer and nothing is pending.
     */
    @PostMapping("/proposals/{id}/accept")
    @PreAuthorize("hasRole('CLIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public AgreementResponse accept(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return agreementService.acceptProposal(id, current.getId());
    }

    @GetMapping("/agreements/my")
    public List<AgreementResponse> mine(@AuthenticationPrincipal AppUserDetails current) {
        return agreementService.mine(current.getId());
    }

    @GetMapping("/agreements/{id}")
    public AgreementResponse get(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return agreementService.get(id, current.getId());
    }

    @PostMapping("/agreements/{id}/wallets")
    public AgreementResponse linkWallet(@AuthenticationPrincipal AppUserDetails current,
                                        @PathVariable Long id,
                                        @Valid @RequestBody LinkWalletRequest request) {
        return agreementService.linkWallet(id, current.getId(), request.address());
    }
}
