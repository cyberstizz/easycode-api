package com.easycode.api.service;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.Asset;
import com.easycode.api.domain.enums.AssetVisibility;
import com.easycode.api.domain.enums.UploadState;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.AssetRepository;
import com.easycode.api.security.AuthPrincipal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Private R2 bucket. The browser gets a presigned URL with a short life and nothing else —
 * no bucket name in the client, no keys, no public objects.
 *
 * <p>R2 is injected through {@link ObjectProvider} because {@code R2Config} is conditional
 * on credentials being present. Without that, an unconfigured bucket fails bean creation and
 * takes down the entire application context — blocking login, projects, requests, and billing,
 * none of which touch file storage. This way the app starts fine and only file operations fail.
 */
@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    private static final String STORAGE_UNCONFIGURED =
            "File storage isn't set up yet. Add your Cloudflare R2 credentials to continue.";

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml", "image/heic",
            "application/pdf", "text/plain", "text/csv",
            "application/zip", "application/x-zip-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "video/mp4", "video/quicktime");

    private final AssetRepository assets;
    private final ObjectProvider<S3Client> r2Provider;
    private final ObjectProvider<S3Presigner> presignerProvider;
    private final AppProperties props;
    private final AuditService audit;

    public AssetService(
            AssetRepository assets,
            ObjectProvider<S3Client> r2Provider,
            ObjectProvider<S3Presigner> presignerProvider,
            AppProperties props,
            AuditService audit) {
        this.assets = assets;
        this.r2Provider = r2Provider;
        this.presignerProvider = presignerProvider;
        this.props = props;
        this.audit = audit;
    }

    private S3Client r2() {
        S3Client client = r2Provider.getIfAvailable();
        if (client == null) {
            throw ApiException.badRequest(STORAGE_UNCONFIGURED);
        }
        return client;
    }

    private S3Presigner presigner() {
        S3Presigner p = presignerProvider.getIfAvailable();
        if (p == null) {
            throw ApiException.badRequest(STORAGE_UNCONFIGURED);
        }
        return p;
    }

    /** True when R2 credentials are configured. Useful for a health or readiness check. */
    public boolean storageAvailable() {
        return r2Provider.getIfAvailable() != null && presignerProvider.getIfAvailable() != null;
    }

    public record Upload(Asset asset, String uploadUrl) {}

    @Transactional
    public Upload presignUpload(
            AuthPrincipal me,
            UUID orgId,
            UUID projectId,
            UUID stageId,
            UUID requestId,
            String filename,
            String mime,
            long bytes) {

        // Resolve storage before writing a row, so a missing bucket doesn't leave
        // an orphaned PENDING asset behind.
        S3Presigner signer = presigner();

        if (filename == null || filename.isBlank()) {
            throw ApiException.badRequest("A filename is required");
        }
        if (bytes <= 0 || bytes > props.getR2().getMaxUploadBytes()) {
            throw ApiException.badRequest(
                    "Files must be under " + (props.getR2().getMaxUploadBytes() / 1_048_576) + " MB");
        }
        if (mime == null || !ALLOWED_MIME.contains(mime.toLowerCase())) {
            throw ApiException.badRequest("That file type isn't accepted");
        }

        String safeName = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        String key = "orgs/%s/%s/%s-%s"
                .formatted(
                        orgId,
                        projectId == null ? "general" : projectId.toString(),
                        UUID.randomUUID(),
                        safeName);

        Asset asset = new Asset();
        asset.setOrgId(orgId);
        asset.setProjectId(projectId);
        asset.setStageId(stageId);
        asset.setRequestId(requestId);
        asset.setUploadedBy(me.userId());
        asset.setR2Key(key);
        asset.setFilename(filename);
        asset.setMime(mime);
        asset.setBytes(bytes);
        asset.setVisibility(AssetVisibility.CLIENT);
        asset.setUploadState(UploadState.PENDING);
        Asset saved = assets.save(asset);

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.getR2().getBucket())
                .key(key)
                .contentType(mime)
                .build();

        String url = signer
                .presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(props.getR2().getUploadUrlTtlMinutes()))
                        .putObjectRequest(put)
                        .build())
                .url()
                .toString();

        return new Upload(saved, url);
    }

    /** Called by the browser once the PUT succeeds. Until then the row stays PENDING and hidden. */
    @Transactional
    public Asset markUploaded(AuthPrincipal me, Asset asset) {
        asset.setUploadState(UploadState.READY);
        Asset saved = assets.save(asset);
        audit.record(me, "asset.upload", "asset", asset.getId(),
                Map.of("filename", asset.getFilename(), "bytes", String.valueOf(asset.getBytes())));
        return saved;
    }

    @Transactional(readOnly = true)
    public String downloadUrl(Asset asset) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(props.getR2().getBucket())
                .key(asset.getR2Key())
                .responseContentDisposition("inline; filename=\"" + asset.getFilename() + "\"")
                .build();
        return presigner()
                .presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(props.getR2().getDownloadUrlTtlMinutes()))
                        .getObjectRequest(get)
                        .build())
                .url()
                .toString();
    }

    @Transactional(readOnly = true)
    public List<Asset> forProject(AuthPrincipal me, UUID projectId) {
        return me.isStaff()
                ? assets.findByProjectIdAndUploadStateOrderByCreatedAtDesc(projectId, UploadState.READY)
                : assets.findByProjectIdAndUploadStateAndVisibilityOrderByCreatedAtDesc(
                        projectId, UploadState.READY, AssetVisibility.CLIENT);
    }

    @Transactional(readOnly = true)
    public List<Asset> forRequest(AuthPrincipal me, UUID requestId) {
        List<Asset> found = assets.findByRequestIdAndUploadStateOrderByCreatedAtAsc(requestId, UploadState.READY);
        return me.isStaff()
                ? found
                : found.stream().filter(a -> a.getVisibility() == AssetVisibility.CLIENT).toList();
    }

    @Transactional
    public Asset update(AuthPrincipal me, Asset asset, String caption, AssetVisibility visibility) {
        if (caption != null) {
            asset.setCaption(caption);
        }
        if (visibility != null && me.isStaff()) {
            asset.setVisibility(visibility);
        }
        return assets.save(asset);
    }

    @Transactional
    public void delete(AuthPrincipal me, Asset asset) {
        try {
            r2().deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getR2().getBucket())
                    .key(asset.getR2Key())
                    .build());
        } catch (Exception e) {
            // orphaned object in R2 is better than a dangling row the client can still see
            log.error("R2 delete failed for key {}", asset.getR2Key(), e);
        }
        assets.delete(asset);
        audit.record(me, "asset.delete", "asset", asset.getId(), Map.of("filename", asset.getFilename()));
    }
}