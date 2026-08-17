package com.easycode.api.web.dto;

import com.easycode.api.domain.Contact;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.enums.DealTier;
import com.easycode.api.domain.enums.OrgStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrgDtos {

    private OrgDtos() {}

    public record OrgUpsert(
            @NotBlank String name,
            String industry,
            String website,
            String phone,
            String address,
            String notes,
            DealTier dealTier,
            OrgStatus status) {}

    public record ContactUpsert(
            @NotBlank String name,
            @NotBlank @Email String email,
            String phone,
            String role,
            boolean isPrimary) {}

    public record ContactView(
            UUID id, String name, String email, String phone, String role, boolean isPrimary, boolean hasLogin) {
        public static ContactView of(Contact c) {
            return new ContactView(
                    c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getRole(),
                    c.isPrimaryContact(), c.getUserId() != null);
        }
    }

    public record OrgView(
            UUID id,
            String name,
            String industry,
            String website,
            String phone,
            String address,
            String notes,
            DealTier dealTier,
            OrgStatus status,
            Instant createdAt,
            List<ContactView> contacts) {

        public static OrgView of(Organization o, List<Contact> contacts) {
            return new OrgView(
                    o.getId(), o.getName(), o.getIndustry(), o.getWebsite(), o.getPhone(), o.getAddress(),
                    o.getNotes(), o.getDealTier(), o.getStatus(), o.getCreatedAt(),
                    contacts == null ? List.of() : contacts.stream().map(ContactView::of).toList());
        }

        public static OrgView summary(Organization o) {
            return of(o, List.of());
        }
    }
}
