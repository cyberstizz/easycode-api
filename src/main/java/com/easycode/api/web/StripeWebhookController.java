package com.easycode.api.web;

import com.easycode.api.service.StripeWebhookService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated by design — the Stripe-Signature header is the authentication.
 * Takes the RAW body: never bind this to a DTO or the signature check breaks.
 */
@RestController
@RequestMapping("/v1/stripe")
public class StripeWebhookController {

    private final StripeWebhookService webhooks;

    public StripeWebhookController(StripeWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping(value = "/webhook", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Map<String, Object>> receive(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        webhooks.handle(payload, signature);
        return ResponseEntity.ok(Map.of("received", true));
    }
}
