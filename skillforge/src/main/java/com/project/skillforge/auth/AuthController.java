package com.project.skillforge.auth;

import com.project.skillforge.users.User;
import com.project.skillforge.users.UserService;
import com.project.skillforge.users.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService users;
    private final JwtService jwt;
    private final PasswordResetService resets;

    public AuthController(UserService users, JwtService jwt, PasswordResetService resets) {
        this.users = users;
        this.jwt = jwt;
        this.resets = resets;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return toResponse(users.register(request.name(), request.email(), request.password()));
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(users.authenticate(request.email(), request.password()));
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        resets.request(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        resets.reset(request.token(), request.newPassword());
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwt.issue(user), "Bearer", jwt.expiresInSeconds(), UserResponse.from(user));
    }
}
