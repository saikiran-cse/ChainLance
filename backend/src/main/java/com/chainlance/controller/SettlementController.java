package com.chainlance.controller;

import com.chainlance.dto.AcceptSettlementRequest;
import com.chainlance.dto.ProposeSettlementRequest;
import com.chainlance.dto.SettlementResponse;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.SettlementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/milestones/{id}/settlements")
    public SettlementResponse proposeSettlement(@AuthenticationPrincipal AppUserDetails current,
                                                @PathVariable Long id,
                                                @Valid @RequestBody ProposeSettlementRequest request) {
        return settlementService.proposeSettlement(id, current.getId(), request);
    }

    @GetMapping("/milestones/{id}/settlements")
    public List<SettlementResponse> getSettlements(@AuthenticationPrincipal AppUserDetails current,
                                                   @PathVariable Long id) {
        return settlementService.getSettlements(id, current.getId());
    }

    @PostMapping("/settlements/{id}/accept")
    public SettlementResponse acceptSettlement(@AuthenticationPrincipal AppUserDetails current,
                                               @PathVariable Long id,
                                               @Valid @RequestBody AcceptSettlementRequest request) {
        return settlementService.acceptSettlement(id, current.getId(), request);
    }

    @PostMapping("/settlements/{id}/withdraw")
    public SettlementResponse withdrawSettlement(@AuthenticationPrincipal AppUserDetails current,
                                                 @PathVariable Long id) {
        return settlementService.withdrawSettlement(id, current.getId());
    }
}
