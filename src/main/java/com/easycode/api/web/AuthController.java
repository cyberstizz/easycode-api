package com.easycode.api.web;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.UserAccount;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.UserRepository;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.service.AuthService;
import com.easycode.api.web.dto.AuthDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final UserRepository users;
    private final OrganizationRepository orgs;
    private final AppProperties props;

    public AuthController(
            AuthService auth, UserRepository users, OrganizationRepository orgs, AppProperties props) {
        this.auth = auth;
        this.users = users;
        this.orgs = orgs;
        this.props = props;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.SessionResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest body, HttpServletRequest http) {
        return respond(auth.login(body.email(), body.password(), agent(http), ip(http)));
    }

    /** Silent refresh — the SPA calls this on mount and on 401. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.SessionResponse> refresh(
            @CookieValue(name = "${app.jwt.cookie-name}", required = false) String refreshCookie,
            HttpServletRequest http) {
        return respond(auth.refresh(refreshCookie, agent(http), ip(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @CookieValue(name = "${app.jwt.cookie-name}", required = false) String refreshCookie) {
        auth.logout(refreshCookie);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .body(Map.of("ok", true));
    }

    @GetMapping("/me")
    public AuthDtos.UserView me(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("No session");
        }
        UserAccount user = users.findById(principal.userId())
                .orElseThrow(() -> ApiException.unauthorized("No session"));
        return AuthDtos.UserView.of(user, orgName(user));
    }

    @GetMapping("/invites/{token}")
    public AuthDtos.InvitePreviewResponse previewInvite(@PathVariable String token) {
        AuthService.InvitePreview preview = auth.previewInvite(token);
        return new AuthDtos.InvitePreviewResponse(
                preview.email(), preview.name(), preview.orgName(), preview.valid());
    }

    @PostMapping("/invites/accept")
    public ResponseEntity<AuthDtos.SessionResponse> acceptInvite(
            @Valid @RequestBody AuthDtos.AcceptInviteRequest body, HttpServletRequest http) {
        return respond(auth.acceptInvite(body.token(), body.password(), body.name(), agent(http), ip(http)));
    }

    @PostMapping("/password/forgot")
    public Map<String, Object> forgot(@Valid @RequestBody AuthDtos.ForgotPasswordRequest body) {
        auth.forgotPassword(body.email());
        // deliberately identical whether or not the address exists
        return Map.of("ok", true);
    }

    @PostMapping("/password/reset")
    public Map<String, Object> reset(@Valid @RequestBody AuthDtos.ResetPasswordRequest body) {
        auth.resetPassword(body.token(), body.password());
        return Map.of("ok", true);
    }

    // ----------------------------------------------------------------- shared

    private ResponseEntity<AuthDtos.SessionResponse> respond(AuthService.Session session) {
        ResponseCookie cookie = cookie(
                session.refreshToken(), Duration.ofSeconds(props.getJwt().getRefreshTtlSeconds()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthDtos.SessionResponse(
                        session.accessToken(),
                        session.expiresIn(),
                        AuthDtos.UserView.of(session.user(), orgName(session.user()))));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(props.getJwt().getCookieName(), value)
                .httpOnly(true)
                .secure(props.getJwt().isCookieSecure())
                .sameSite(props.getJwt().getCookieSameSite())
                .path("/")
                .maxAge(maxAge);
        String domain = props.getJwt().getCookieDomain();
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }
        return builder.build();
    }

    private String orgName(UserAccount user) {
        return user.getOrgId() == null
                ? null
                : orgs.findById(user.getOrgId()).map(Organization::getName).orElse(null);
    }

    private String agent(HttpServletRequest http) {
        return http.getHeader("User-Agent");
    }

    private String ip(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? http.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }
}
