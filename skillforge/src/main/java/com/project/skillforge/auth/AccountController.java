package com.project.skillforge.auth;

import com.project.skillforge.users.User;
import com.project.skillforge.users.UserResponse;
import com.project.skillforge.users.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Credential-sensitive actions on the signed-in account. */
@RestController
@RequestMapping("/api/users/me")
public class AccountController {

    private final UserService users;
    private final JwtService jwt;

    public AccountController(UserService users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    /** Returns a fresh token because the change invalidates all previously issued ones. */
    @PutMapping("/password")
    AuthResponse changePassword(Authentication auth, @Valid @RequestBody ChangePasswordRequest request) {
        User user = users.changePassword(UUID.fromString(auth.getName()), request.currentPassword(),
                request.newPassword());
        return new AuthResponse(jwt.issue(user), "Bearer", jwt.expiresInSeconds(), UserResponse.from(user));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteAccount(Authentication auth, @Valid @RequestBody DeleteAccountRequest request) {
        users.deleteAccount(UUID.fromString(auth.getName()), request.password());
    }
}