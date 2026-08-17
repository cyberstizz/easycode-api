package com.easycode.api.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    /** Where the SPA lives. Used to build invite / reset links. */
    private String baseUrl = "http://localhost:5173";

    private String supportEmail = "hello@easycode.dev";
    private List<String> corsOrigins = List.of("http://localhost:5173");

    private Jwt jwt = new Jwt();
    private Invite invite = new Invite();
    private Reset reset = new Reset();
    private R2 r2 = new R2();
    private Resend resend = new Resend();
    private Stripe stripe = new Stripe();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private String issuer = "easycode-api";
        private long accessTtlSeconds = 900;
        private long refreshTtlSeconds = 2592000;
        private String cookieName = "ec_rt";
        private boolean cookieSecure = true;
        private String cookieSameSite = "None";
        private String cookieDomain = "";
    }

    @Getter
    @Setter
    public static class Invite {
        private int ttlHours = 168;
    }

    @Getter
    @Setter
    public static class Reset {
        private int ttlMinutes = 60;
    }

    @Getter
    @Setter
    public static class R2 {
        private String accountId;
        private String accessKeyId;
        private String secretAccessKey;
        private String bucket = "easycode-client-assets";
        private int uploadUrlTtlMinutes = 15;
        private int downloadUrlTtlMinutes = 60;
        private long maxUploadBytes = 52_428_800L;
    }

    @Getter
    @Setter
    public static class Resend {
        private String apiKey;
        private String from = "EasyCode <hello@easycode.dev>";
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Stripe {
        private String secretKey;
        private String webhookSecret;
        private String currency = "usd";
    }
}
