package com.easycode.api.repo;

import com.easycode.api.domain.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByStripeCustomerId(String stripeCustomerId);

    @Query("select o from Organization o where lower(o.name) like lower(concat('%', :q, '%'))")
    Page<Organization> search(String q, Pageable pageable);
}
