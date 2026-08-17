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
}
