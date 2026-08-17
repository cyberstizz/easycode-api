package com.easycode.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.UserAccount;
import com.easycode.api.domain.enums.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit tests — no Spring context, no database. */
class JwtServiceTest {

    private AppProperties props(String secret, long ttl) {
        AppProperties p = new AppProperties();
        p.getJwt().setSecret(secret);
        p.getJwt().setAccessTtlSeconds(ttl);
        return p;
    }

    private static final String SECRET = "test-secret-that-is-definitely-long-enough-32+";

    private UserAccount client(UUID orgId) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("owner@harlemsoul.test");
        user.setName("Owner");
        user.setRole(Role.CLIENT);
        user.setOrgId(orgId);
        return user;
    }

    @Test
    void roundTripsIdentityAndTenancy() {
        JwtService jwt = new JwtService(props(SECRET, 900));
        UUID orgId = UUID.randomUUID();
        UserAccount user = client(orgId);

        AuthPrincipal parsed = jwt.parse(jwt.issueAccessToken(user));

        assertThat(parsed).isNotNull();
        assertThat(parsed.userId()).isEqualTo(user.getId());
        assertThat(parsed.email()).isEqualTo("owner@harlemsoul.test");
        assertThat(parsed.role()).isEqualTo(Role.CLIENT);
        assertThat(parsed.orgId()).isEqualTo(orgId);
        assertThat(parsed.isClient()).isTrue();
        assertThat(parsed.isStaff()).isFalse();
    }

    @Test
    void staffTokensCarryNoOrg() {
        JwtService jwt = new JwtService(props(SECRET, 900));
        UserAccount admin = new UserAccount();
        admin.setId(UUID.randomUUID());
        admin.setEmail("charles@easycode.test");
        admin.setName("Charles");
        admin.setRole(Role.ADMIN);

        AuthPrincipal parsed = jwt.parse(jwt.issueAccessToken(admin));

        assertThat(parsed.orgId()).isNull();
        assertThat(parsed.isStaff()).isTrue();
        assertThat(parsed.isAdmin()).isTrue();
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        JwtService mine = new JwtService(props(SECRET, 900));
        JwtService theirs = new JwtService(props("a-completely-different-secret-also-32-bytes", 900));

        assertThat(mine.parse(theirs.issueAccessToken(client(UUID.randomUUID())))).isNull();
    }

    @Test
    void rejectsGarbageAndExpiredTokens() throws Exception {
        JwtService jwt = new JwtService(props(SECRET, 900));
        assertThat(jwt.parse("not-a-jwt")).isNull();
        assertThat(jwt.parse("")).isNull();

        JwtService instant = new JwtService(props(SECRET, -1));
        assertThat(instant.parse(instant.issueAccessToken(client(UUID.randomUUID())))).isNull();
    }

    @Test
    void refusesToStartWithAWeakSecret() {
        assertThatThrownBy(() -> new JwtService(props("too-short", 900)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
}
