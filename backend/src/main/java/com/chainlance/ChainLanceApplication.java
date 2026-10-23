package com.chainlance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChainLanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChainLanceApplication.class, args);
    }
}
