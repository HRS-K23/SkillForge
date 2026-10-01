package com.project.skillforge.users;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    public enum Role { LEARNER, ADMIN }

    public enum Status { ACTIVE, DISABLED, DELETED }

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {}

    public User(String name, String email, String password, Role role) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public Role getRole() { return role; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public int getTokenVersion() { return tokenVersion; }
    public Instant getDeletedAt() { return deletedAt; }

    /** Changes the password and invalidates every token issued before now. */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
        this.tokenVersion++;
    }

    public void softDelete() {
        this.status = Status.DELETED;
        this.deletedAt = Instant.now();
        this.tokenVersion++;
    }
}
