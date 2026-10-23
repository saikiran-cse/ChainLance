package com.chainlance.controller;

import com.chainlance.dto.ChallengeRequest;
import com.chainlance.dto.ChallengeResponse;
import com.chainlance.dto.VerifyRequest;
import com.chainlance.dto.WalletResponse;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.WalletService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /** Step 1: ask for a message to sign for the given address. */
    @PostMapping("/challenge")
    public ChallengeResponse challenge(@AuthenticationPrincipal AppUserDetails current,
                                       @Valid @RequestBody ChallengeRequest request) {
        return walletService.createChallenge(current.getId(), request.address());
    }

    /** Step 2: send the nonce and the MetaMask signature to link the wallet. */
    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse verify(@AuthenticationPrincipal AppUserDetails current,
                                 @Valid @RequestBody VerifyRequest request) {
        return walletService.verify(current.getId(), request.nonce(), request.signature());
    }

    @GetMapping
    public List<WalletResponse> mine(@AuthenticationPrincipal AppUserDetails current) {
        return walletService.list(current.getId());
    }
}
