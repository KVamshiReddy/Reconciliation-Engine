package com.drivewealth.Reconciliation_Engine.alpaca;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "alpaca")
public class AlpacaProperties {

    private String apiKey;
    private String apiSecret;
    private String baseUrl;
    private boolean enabled;

}
