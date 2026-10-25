package com.chainlance.controller;

import com.chainlance.config.ChainProperties;
import com.chainlance.config.PricingProperties;
import com.chainlance.dto.ChainConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chain")
public class ChainConfigController {

    private final ChainProperties chainProperties;
    private final PricingProperties pricingProperties;

    public ChainConfigController(ChainProperties chainProperties, PricingProperties pricingProperties) {
        this.chainProperties = chainProperties;
        this.pricingProperties = pricingProperties;
    }

    @GetMapping("/config")
    public ChainConfigResponse getConfig() {
        return new ChainConfigResponse(
                chainProperties.chainId(),
                chainProperties.factoryAddress(),
                chainProperties.adminAddress(),
                chainProperties.requiredConfirmations(),
                pricingProperties.inrPerEth().toPlainString()
        );
    }
}
