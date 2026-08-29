package com.easycode.api.repo;

import com.easycode.api.domain.Contact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    List<Contact> findByOrgId(UUID orgId);

    Optional<Contact> findByUserId(UUID userId);

    Optional<Contact> findByOrgIdAndEmailIgnoreCase(UUID orgId, String email);

    long countByOrgId(UUID orgId);
}