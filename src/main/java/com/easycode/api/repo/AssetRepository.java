package com.easycode.api.repo;

import com.easycode.api.domain.Asset;
import com.easycode.api.domain.enums.AssetVisibility;
import com.easycode.api.domain.enums.UploadState;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    List<Asset> findByProjectIdAndUploadStateOrderByCreatedAtDesc(UUID projectId, UploadState state);

    List<Asset> findByProjectIdAndUploadStateAndVisibilityOrderByCreatedAtDesc(
            UUID projectId, UploadState state, AssetVisibility visibility);

    List<Asset> findByRequestIdAndUploadStateOrderByCreatedAtAsc(UUID requestId, UploadState state);

    List<Asset> findTop10ByOrgIdAndUploadStateOrderByCreatedAtDesc(UUID orgId, UploadState state);

    /** Every asset for an org, any upload state — used to purge R2 before an org is deleted. */
    List<Asset> findByOrgId(UUID orgId);

    long countByOrgId(UUID orgId);
}