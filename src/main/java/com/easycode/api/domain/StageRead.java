package com.easycode.api.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** When a user last opened a stage. Composite key on (stage, user). */
@Entity
@Table(name = "stage_reads")
@IdClass(StageRead.Key.class)
@Getter
@Setter
public class StageRead {

    @Id
    @Column(name = "stage_id", nullable = false)
    private UUID stageId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    public static class Key implements Serializable {
        private UUID stageId;
        private UUID userId;

        public Key() {}

        public Key(UUID stageId, UUID userId) {
            this.stageId = stageId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(stageId, k.stageId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stageId, userId);
        }
    }
}