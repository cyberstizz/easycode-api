package com.easycode.api.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    private final AppProperties props;

    public StripeConfig(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = props.getStripe().getSecretKey();
    }
}
