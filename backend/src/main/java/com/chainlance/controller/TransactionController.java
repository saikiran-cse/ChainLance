package com.chainlance.controller;

import com.chainlance.dto.AgreementResponse;
import com.chainlance.dto.ReconcileResponse;
import com.chainlance.dto.RegisterEscrowRequest;
import com.chainlance.dto.TransactionResponse;
import com.chainlance.dto.VerifyTransactionRequest;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.ChainVerificationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final ChainVerificationService verificationService;

    public TransactionController(ChainVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /**
     * Client only: registers the deployed escrow contract by verifying the createEscrow transaction.
     */
    @PostMapping("/agreements/{id}/escrow/register")
    @PreAuthorize("hasRole('CLIENT')")
    public AgreementResponse registerEscrow(@AuthenticationPrincipal AppUserDetails current,
                                            @PathVariable Long id,
                                            @Valid @RequestBody RegisterEscrowRequest request) {
        return verificationService.registerEscrow(id, current.getId(), request.txHash());
    }

    /**
     * Verifies the funding transaction of an agreement's escrow.
     */
    @PostMapping("/agreements/{id}/escrow/verify-fund")
    public TransactionResponse verifyFund(@AuthenticationPrincipal AppUserDetails current,
                                          @PathVariable Long id,
                                          @Valid @RequestBody RegisterEscrowRequest request) {
        return verificationService.verifyFunding(request.txHash(), id, current.getId());
    }

    /**
     * Generic transaction verification endpoint.
     */
    @PostMapping("/transactions/verify")
    public TransactionResponse verifyTransaction(@AuthenticationPrincipal AppUserDetails current,
                                                 @Valid @RequestBody VerifyTransactionRequest request) {
        return verificationService.verifyTransaction(request, current.getId());
    }

    /**
     * Reconciles on-chain escrow state forward into MySQL.
     */
    @PostMapping("/agreements/{id}/reconcile")
    public ReconcileResponse reconcile(@AuthenticationPrincipal AppUserDetails current,
                                       @PathVariable Long id) {
        return verificationService.reconcile(id, current.getId(), current.getRole());
    }

    /**
     * Lists blockchain transactions submitted by the current user.
     */
    @GetMapping("/transactions/mine")
    public List<TransactionResponse> mine(@AuthenticationPrincipal AppUserDetails current) {
        return verificationService.listMine(current.getId());
    }
}
