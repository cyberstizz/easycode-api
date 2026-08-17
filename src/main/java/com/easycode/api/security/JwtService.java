package com.easycode.api.security;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.UserAccount;
import com.easycode.api.domain.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.security.Keys;

/** Short-lived access tokens only. Refresh is an opaque, rotating, DB-backed cookie. */
@Service
public class JwtService {

    private final AppProperties props;
    private final SecretKey key;

    public JwtService(AppProperties props) {
        this.props = props;
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least 32 bytes (openssl rand -base64 48)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getJwt().getAccessTtlSeconds());
        return Jwts.builder()
                .issuer(props.getJwt().getIssuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .claim("orgId", user.getOrgId() == null ? null : user.getOrgId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public long accessTtlSeconds() {
        return props.getJwt().getAccessTtlSeconds();
    }

    /** Returns null when the token is missing, malformed, expired or tampered with. */
    public AuthPrincipal parse(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String orgId = c.get("orgId", String.class);
            return new AuthPrincipal(
                    UUID.fromString(c.getSubject()),
                    c.get("email", String.class),
                    c.get("name", String.class),
                    Role.valueOf(c.get("role", String.class)),
                    orgId == null ? null : UUID.fromString(orgId));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
