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

    public AuthController(UserService users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
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

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwt.issue(user), "Bearer", jwt.expiresInSeconds(), UserResponse.from(user));
    }
}
