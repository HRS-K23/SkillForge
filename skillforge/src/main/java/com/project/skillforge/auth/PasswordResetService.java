package com.project.skillforge.auth;

import com.project.skillforge.shared.error.ApiException;
import com.project.skillforge.users.User;
import com.project.skillforge.users.UserRepository;
import com.project.skillforge.users.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final Duration MIN_INTERVAL = Duration.ofMinutes(1);

    private final UserRepository users;
    private final UserService userService;
    private final PasswordResetTokenRepository tokens;
    private final ResetLinkSender sender;
    private final Duration ttl;
    private final String frontendBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, UserService userService, PasswordResetTokenRepository tokens,
                                ResetLinkSender sender,
                                @Value("${skillforge.password-reset.expiration-minutes:30}") long minutes,
                                @Value("${skillforge.frontend.base-url:http://localhost:3000}") String baseUrl) {
        this.users = users;
        this.userService = userService;
        this.tokens = tokens;
        this.sender = sender;
        this.ttl = Duration.ofMinutes(minutes);
        this.frontendBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Silent when the email is unknown so accounts cannot be enumerated. */
    @Transactional
    public void request(String email) {
        User user = users.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null || user.getStatus() != User.Status.ACTIVE) {
            return;
        }
        List<PasswordResetToken> open = tokens.findByUserIdAndUsedAtIsNull(user.getId());
        Instant now = Instant.now();
        if (open.stream().anyMatch(t -> t.getCreatedAt().isAfter(now.minus(MIN_INTERVAL)))) {
            return;
        }
        open.forEach(PasswordResetToken::markUsed);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new PasswordResetToken(user.getId(), hash(raw), now.plus(ttl)));
        sender.send(user, frontendBaseUrl + "/reset-password?token=" + raw);
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken token = tokens.findByTokenHash(hash(rawToken)).orElse(null);
        User user = token == null ? null : users.findById(token.getUserId()).orElse(null);
        if (token == null || user == null || token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())
                || user.getStatus() != User.Status.ACTIVE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESET_TOKEN",
                    "This reset link is invalid or has expired");
        }
        userService.setPasswordFromReset(user, newPassword);
        tokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(PasswordResetToken::markUsed);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}