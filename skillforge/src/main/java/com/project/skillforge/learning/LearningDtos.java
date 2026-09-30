package com.project.skillforge.learning;

import java.util.List;
import java.util.UUID;

public final class LearningDtos {
    private LearningDtos() {}

    public record LessonSummary(UUID id, String title, Integer estimatedTime, int order) {}

    public record ExerciseResponse(UUID id, String title, String description) {}

    public record ModuleResponse(UUID id, String title, String description, int order,
                                 List<LessonSummary> lessons, List<ExerciseResponse> exercises) {}

    public record LearningPathResponse(UUID id, String toolSlug, String title, String description,
                                       List<ModuleResponse> modules) {}

    public record LessonResponse(UUID id, UUID moduleId, String toolSlug, String title, Integer estimatedTime, String youtubeUrl,
                                 String youtubeEmbedUrl, String content) {}
}

