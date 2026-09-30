package com.project.skillforge.users;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    UserResponse me(Authentication auth) {
        return service.getProfile(UUID.fromString(auth.getName()));
    }

    @PutMapping
    UserResponse update(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(UUID.fromString(auth.getName()), request);
    }
}
