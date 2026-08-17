package com.easycode.api.security;

import com.easycode.api.domain.enums.Role;
import java.util.UUID;

/**
 * What every controller gets via @AuthenticationPrincipal.
 * orgId is null for staff and non-null for clients — it is the tenancy key.
 */
public record AuthPrincipal(UUID userId, String email, String name, Role role, UUID orgId) {

    public boolean isStaff() {
        return role == Role.ADMIN || role == Role.AGENT;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isClient() {
        return role == Role.CLIENT;
    }
}
