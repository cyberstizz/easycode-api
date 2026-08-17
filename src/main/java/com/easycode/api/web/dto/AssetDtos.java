package com.easycode.api.web.dto;

import com.easycode.api.domain.Asset;
import com.easycode.api.domain.enums.AssetVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public final class AssetDtos {

    private AssetDtos() {}

    public record PresignRequest(
            UUID orgId,
            UUID projectId,
            UUID stageId,
            UUID requestId,
            @NotBlank String filename,
            @NotBlank String mime,
            @Positive long bytes) {}

    public record PresignResponse(UUID assetId, String uploadUrl, String key, int expiresInMinutes) {}

    public record AssetUpdate(String caption, AssetVisibility visibility) {}

    public record AssetView(
            UUID id,
            UUID projectId,
            UUID requestId,
            String filename,
            String mime,
            Long bytes,
            String caption,
            AssetVisibility visibility,
            Instant createdAt) {

        public static AssetView of(Asset a) {
            return new AssetView(
                    a.getId(), a.getProjectId(), a.getRequestId(), a.getFilename(), a.getMime(),
                    a.getBytes(), a.getCaption(), a.getVisibility(), a.getCreatedAt());
        }
    }

    public record DownloadResponse(String url, int expiresInMinutes) {}
}
