package com.easycode.api.service;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.Contact;
import com.easycode.api.domain.Invite;
import com.easycode.api.domain.Organization;
import com.easycode.api.domain.PasswordReset;
import com.easycode.api.domain.RefreshToken;
import com.easycode.api.domain.UserAccount;
import com.easycode.api.domain.enums.Role;
import com.easycode.api.domain.enums.UserStatus;
import com.easycode.api.error.ApiException;
import com.easycode.api.repo.ContactRepository;
import com.easycode.api.repo.InviteRepository;
import com.easycode.api.repo.OrganizationRepository;
import com.easycode.api.repo.PasswordResetRepository;
import com.easycode.api.repo.RefreshTokenRepository;
import com.easycode.api.repo.UserRepository;
import com.easycode.api.security.AuthPrincipal;
import com.easycode.api.security.JwtService;
import com.easycode.api.security.Tokens;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decision 2: our own auth, not Supabase Auth — because the key flow is
 * "admin creates the account once a prospect becomes a client".
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final ContactRepository contacts;
    private final OrganizationRepository orgs;
    private final InviteRepository invites;
    private final PasswordResetRepository resets;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final EmailService email;
    private final AppProperties props;

    public AuthService(
            UserRepository users,
            ContactRepository contacts,
            OrganizationRepository orgs,
            InviteRepository invites,
            PasswordResetRepository resets,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder encoder,
            JwtService jwt,
            EmailService email,
            AppProperties props) {
        this.users = users;
        this.contacts = contacts;
        this.orgs = orgs;
        this.invites = invites;
        this.resets = resets;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.email = email;
        this.props = props;
    }

    public record Session(String accessToken, long expiresIn, String refreshToken, UserAccount user) {}

    public record InvitePreview(String email, String name, String orgName, boolean valid) {}

    // --------------------------------------------------- password confirmation

    /**
     * Confirms the signed-in user really typed their own password. Gate for destructive
     * actions. Deliberately not a login: no session is issued, lastLoginAt is untouched,
     * and the failure is 401 so the frontend shows it against the password field rather
     * than bouncing the user to the sign-in screen.
     */
    @Transactional(readOnly = true)
    public void requireCurrentPassword(AuthPrincipal me, String password) {
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("Enter your password to confirm");
        }
        UserAccount user = users.findById(me.userId())
                .orElseThrow(() -> ApiException.unauthorized("Sign in again"));
        if (user.getPasswordHash() == null || !encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("That password isn't right");
        }
    }

    // ------------------------------------------------------------------ login

    @Transactional
    public Session login(String rawEmail, String password, String userAgent, String ip) {
        String emailAddr = normalize(rawEmail);
        UserAccount user = users.findByEmailIgnoreCase(emailAddr)
                .orElseThrow(() -> ApiException.unauthorized("Email or password is incorrect"));

        if (user.getPasswordHash() == null || !encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("Email or password is incorrect");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.forbidden();
        }
        if (user.getStatus() == UserStatus.INVITED) {
            // has a password but never finished setup — treat as active from here
            user.setStatus(UserStatus.ACTIVE);
        }

        user.setLastLoginAt(Instant.now());
        users.save(user);
        return issue(user, userAgent, ip);
    }

    // ---------------------------------------------------------------- refresh

    @Transactional
    public Session refresh(String rawRefreshToken, String userAgent, String ip) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("No session");
        }
        RefreshToken stored = refreshTokens
                .findByTokenHash(Tokens.hash(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Session expired"));

        if (!stored.isUsable()) {
            // a revoked token being replayed means the cookie leaked — drop every session for that user
            refreshTokens.revokeAllForUser(stored.getUserId(), Instant.now());
            log.warn("Replayed refresh token for user {} — all sessions revoked", stored.getUserId());
            throw ApiException.unauthorized("Session expired");
        }

        UserAccount user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Session expired"));
        if (user.getStatus() == UserStatus.DISABLED) {
            throw ApiException.forbidden();
        }

        Session next = issue(user, userAgent, ip);
        stored.setRevokedAt(Instant.now());
        refreshTokens.save(stored);
        return next;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(Tokens.hash(rawRefreshToken)).ifPresent(t -> {
            t.setRevokedAt(Instant.now());
            refreshTokens.save(t);
        });
    }

    private Session issue(UserAccount user, String userAgent, String ip) {
        String raw = Tokens.random();
        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(Tokens.hash(raw));
        token.setExpiresAt(Instant.now().plusSeconds(props.getJwt().getRefreshTtlSeconds()));
        token.setUserAgent(truncate(userAgent, 250));
        token.setIp(ip);
        refreshTokens.save(token);
        return new Session(jwt.issueAccessToken(user), jwt.accessTtlSeconds(), raw, user);
    }

    // ---------------------------------------------------------------- invites

    @Transactional(readOnly = true)
    public InvitePreview previewInvite(String rawToken) {
        Optional<Invite> found = invites.findByTokenHash(Tokens.hash(rawToken));
        if (found.isEmpty()) {
            return new InvitePreview(null, null, null, false);
        }
        Invite invite = found.get();
        boolean valid = invite.getUsedAt() == null && invite.getExpiresAt().isAfter(Instant.now());
        Contact contact = contacts.findById(invite.getContactId()).orElse(null);
        String orgName = contact == null
                ? null
                : orgs.findById(contact.getOrgId()).map(Organization::getName).orElse(null);
        return new InvitePreview(
                invite.getEmail(), contact == null ? null : contact.getName(), orgName, valid);
    }

    @Transactional
    public Session acceptInvite(String rawToken, String password, String name, String userAgent, String ip) {
        requireStrong(password);
        Invite invite = invites.findByTokenHash(Tokens.hash(rawToken))
                .orElseThrow(() -> ApiException.badRequest("That invite link is not valid"));
        if (invite.getUsedAt() != null) {
            throw ApiException.badRequest("That invite has already been used");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("That invite has expired — ask for a new one");
        }

        Contact contact = contacts.findById(invite.getContactId())
                .orElseThrow(() -> ApiException.badRequest("That invite is no longer valid"));

        UserAccount user = users.findByEmailIgnoreCase(invite.getEmail()).orElseGet(UserAccount::new);
        user.setEmail(normalize(invite.getEmail()));
        user.setName(name != null && !name.isBlank() ? name.trim() : contact.getName());
        user.setOrgId(contact.getOrgId());
        if (user.getRole() == null) {
            user.setRole(Role.CLIENT);
        }
        user.setPasswordHash(encoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(Instant.now());
        users.save(user);

        contact.setUserId(user.getId());
        contacts.save(contact);

        invite.setUsedAt(Instant.now());
        invites.save(invite);

        return issue(user, userAgent, ip);
    }

    // -------------------------------------------------------- password resets

    @Transactional
    public void forgotPassword(String rawEmail) {
        // always returns quietly — never confirm whether an address exists
        users.findByEmailIgnoreCase(normalize(rawEmail)).ifPresent(user -> {
            String raw = Tokens.random();
            PasswordReset reset = new PasswordReset();
            reset.setUserId(user.getId());
            reset.setTokenHash(Tokens.hash(raw));
            reset.setExpiresAt(Instant.now().plus(props.getReset().getTtlMinutes(), ChronoUnit.MINUTES));
            resets.save(reset);
            email.sendPasswordReset(
                    user.getEmail(), props.getBaseUrl() + "/reset-password?token=" + raw);
        });
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        requireStrong(password);
        PasswordReset reset = resets.findByTokenHash(Tokens.hash(rawToken))
                .orElseThrow(() -> ApiException.badRequest("That reset link is not valid"));
        if (reset.getUsedAt() != null || reset.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("That reset link has expired — request a new one");
        }
        UserAccount user = users.findById(reset.getUserId())
                .orElseThrow(() -> ApiException.badRequest("That reset link is not valid"));

        user.setPasswordHash(encoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);

        reset.setUsedAt(Instant.now());
        resets.save(reset);

        // changing a password kills every other session
        refreshTokens.revokeAllForUser(user.getId(), Instant.now());
    }

    // ----------------------------------------------------------------- shared

    private void requireStrong(String password) {
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("Password must be at least 8 characters");
        }
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String truncate(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }
}