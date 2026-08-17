package com.easycode.api.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "request_reads")
@IdClass(RequestRead.Key.class)
@Getter
@Setter
public class RequestRead {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt = Instant.now();

    @Getter
    @Setter
    public static class Key implements Serializable {
        private UUID requestId;
        private UUID userId;

        public Key() {}

        public Key(UUID requestId, UUID userId) {
            this.requestId = requestId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(requestId, key.requestId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestId, userId);
        }
    }
}
