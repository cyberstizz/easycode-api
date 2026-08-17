package com.easycode.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void generatesUrlSafeUniqueTokens() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = Tokens.random();
            assertThat(token).matches("[A-Za-z0-9_-]+");
            assertThat(seen.add(token)).isTrue();
        }
    }

    @Test
    void hashesAreStableAndOneWay() {
        String raw = Tokens.random();
        assertThat(Tokens.hash(raw)).isEqualTo(Tokens.hash(raw));
        assertThat(Tokens.hash(raw)).hasSize(64).doesNotContain(raw);
        assertThat(Tokens.hash(raw)).isNotEqualTo(Tokens.hash(Tokens.random()));
    }
}
