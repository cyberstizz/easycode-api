package com.easycode.api.web;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.Asset;
import com.easycode.api.domain.ClientRequest;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.AccessService;
import com.easycode.api.service.AssetService;
import com.easycode.api.web.dto.AssetDtos;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/assets")
public class AssetController {

    private final AssetService assets;
    private final AccessService access;
    private final AppProperties props;

    public AssetController(AssetService assets, AccessService access, AppProperties props) {
        this.assets = assets;
        this.access = access;
        this.props = props;
    }

    /** Step 1: ask for a slot. Step 2: PUT the bytes straight to R2. Step 3: POST /complete. */
    @PostMapping("/presign")
    public AssetDtos.PresignResponse presign(
            @AuthenticationPrincipal AuthPrincipal me, @Valid @RequestBody AssetDtos.PresignRequest body) {

        UUID orgId = access.resolveOrgId(me, body.orgId());
        access.requireOrg(me, orgId);
        if (body.projectId() != null) {
            access.project(me, body.projectId());
        }
        if (body.requestId() != null) {
            ClientRequest request = access.request(me, body.requestId());
            orgId = request.getOrgId();
        }

        AssetService.Upload upload = assets.presignUpload(
                me, orgId, body.projectId(), body.stageId(), body.requestId(),
                body.filename(), body.mime(), body.bytes());

        return new AssetDtos.PresignResponse(
                upload.asset().getId(),
                upload.uploadUrl(),
                upload.asset().getR2Key(),
                props.getR2().getUploadUrlTtlMinutes());
    }

    @PostMapping("/{id}/complete")
    public AssetDtos.AssetView complete(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        Asset asset = access.asset(me, id);
        return AssetDtos.AssetView.of(assets.markUploaded(me, asset));
    }

    @GetMapping
    public List<AssetDtos.AssetView> list(
            @AuthenticationPrincipal AuthPrincipal me,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID requestId) {

        if (projectId != null) {
            access.project(me, projectId);
            return assets.forProject(me, projectId).stream().map(AssetDtos.AssetView::of).toList();
        }
        if (requestId != null) {
            access.request(me, requestId);
            return assets.forRequest(me, requestId).stream().map(AssetDtos.AssetView::of).toList();
        }
        return List.of();
    }

    /** Short-lived signed GET. Nothing in the bucket is ever public. */
    @GetMapping("/{id}/url")
    public AssetDtos.DownloadResponse url(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        Asset asset = access.asset(me, id);
        return new AssetDtos.DownloadResponse(
                assets.downloadUrl(asset), props.getR2().getDownloadUrlTtlMinutes());
    }

    @PatchMapping("/{id}")
    public AssetDtos.AssetView update(
            @AuthenticationPrincipal AuthPrincipal me,
            @PathVariable UUID id,
            @RequestBody AssetDtos.AssetUpdate body) {
        Asset asset = access.asset(me, id);
        return AssetDtos.AssetView.of(assets.update(me, asset, body.caption(), body.visibility()));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(
            @AuthenticationPrincipal AuthPrincipal me, @PathVariable UUID id) {
        Asset asset = access.asset(me, id);
        if (!me.isStaff() && !me.userId().equals(asset.getUploadedBy())) {
            throw com.easycode.api.error.ApiException.forbidden();
        }
        assets.delete(me, asset);
        return Map.of("ok", true);
    }
}
