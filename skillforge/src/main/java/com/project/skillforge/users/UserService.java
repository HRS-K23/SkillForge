package com.project.skillforge.users;

import com.project.skillforge.shared.error.ApiException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Set<String> adminEmails;

    public UserService(UserRepository users, PasswordEncoder encoder,
                       @Value("${skillforge.admin.emails:}") String adminEmails) {
        this.users = users;
        this.encoder = encoder;
        this.adminEmails = Arrays.stream(adminEmails.split(",")).map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    @Transactional
    public User register(String name, String email, String rawPassword) {
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email is already registered");
        }
        return users.save(new User(name.trim(), normalized, encoder.encode(rawPassword),
                adminEmails.contains(normalized) ? User.Role.ADMIN : User.Role.LEARNER));
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String rawPassword) {
        User user = users.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null || user.getStatus() != User.Status.ACTIVE
                || !encoder.matches(rawPassword, user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public UserResponse updateProfile(UUID id, UpdateProfileRequest request) {
        User user = find(id);
        user.setName(request.name().trim());
        return UserResponse.from(user);
    }

    private User find(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }
}
