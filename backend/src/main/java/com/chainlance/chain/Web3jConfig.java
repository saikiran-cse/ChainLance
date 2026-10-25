package com.chainlance.chain;

import com.chainlance.config.ChainProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3jConfig {

    @Bean
    @ConditionalOnMissingBean(Web3j.class)
    public Web3j web3j(ChainProperties properties) {
        String url = properties.rpcUrl();
        if (url == null || url.isBlank()) {
            url = "http://127.0.0.1:8545";
        }
        return Web3j.build(new HttpService(url));
    }
}
