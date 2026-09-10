package com.jobscraper.infrastructure.email;

import com.jobscraper.configuration.ResendProperties;
import com.resend.Resend;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the Resend client.
 *
 * <p>The {@link Resend} bean is only created when {@code resend.enabled=true}, so local
 * development and tests do not require a real Resend API key.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "resend", name = "enabled", havingValue = "true")
public class ResendConfiguration {

    /**
     * Creates the Resend API client using the configured API key.
     *
     * <p>The API key is resolved from {@code resend.apiKey} (environment variable
     * {@code RESEND_API_KEY}), never hardcoded.</p>
     *
     * @param properties the Resend configuration
     * @return a {@link Resend} client
     */
    @Bean
    public Resend resendClient(ResendProperties properties) {
        return new Resend(properties.apiKey());
    }
}

