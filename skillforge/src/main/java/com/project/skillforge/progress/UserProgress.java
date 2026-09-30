package com.project.skillforge.progress;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_progress")
public class UserProgress {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "lesson_id", nullable = false) private UUID lessonId;
    @Column(nullable = false) private boolean completed;
    @Column(name = "completed_at") private Instant completedAt;

    protected UserProgress() {}

    public UserProgress(UUID userId, UUID lessonId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.lessonId = lessonId;
    }

    public void setCompleted(boolean completed) {
        if (completed && !this.completed) {
            this.completedAt = Instant.now();
        } else if (!completed) {
            this.completedAt = null;
        }
        this.completed = completed;
    }

    public UUID getLessonId() { return lessonId; }
    public boolean isCompleted() { return completed; }
    public Instant getCompletedAt() { return completedAt; }
}
