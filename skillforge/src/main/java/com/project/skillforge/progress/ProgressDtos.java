package com.project.skillforge.progress;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProgressDtos {
    private ProgressDtos() {}

    public record SetCompletedRequest(boolean completed) {}

    public record LessonProgress(UUID lessonId, boolean completed, Instant completedAt) {}

    public record ModuleProgress(UUID moduleId, String title, int totalLessons, int completedLessons, int percent,
                                 List<LessonProgress> lessons) {}

    public record ToolProgress(String toolSlug, String toolName, int totalLessons, int completedLessons, int percent,
                               List<ModuleProgress> modules) {}
}
