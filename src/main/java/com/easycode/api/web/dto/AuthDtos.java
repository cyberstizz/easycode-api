package com.easycode.api.web.dto;

import com.easycode.api.domain.UserAccount;
import com.easycode.api.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record AcceptInviteRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
            String name) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password) {}

    public record UserView(UUID id, String email, String name, Role role, UUID orgId, String orgName) {
        public static UserView of(UserAccount user, String orgName) {
            return new UserView(
                    user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getOrgId(), orgName);
        }
    }

    public record SessionResponse(String accessToken, long expiresIn, UserView user) {}

    public record InvitePreviewResponse(String email, String name, String orgName, boolean valid) {}
}
