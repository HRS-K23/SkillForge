package com.project.skillforge.admin;

import com.project.skillforge.admin.ContentAuthoringService.Written;
import com.project.skillforge.admin.ContentReloadService.ReloadResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (see SecurityConfig) content authoring and reload. */
@RestController
@RequestMapping("/api/admin")
public class AdminContentController {

    public record ToolRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
                    message = "must be lowercase letters, digits and hyphens") String slug,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 50) String category,
            @Size(max = 300) @Pattern(regexp = "(https?://.*)?", message = "must be an http(s) URL") String logoUrl) {}

    public record ModuleRequest(@NotBlank @Size(max = 150) String title, @Size(max = 500) String description) {}

    public record LessonRequest(
            @NotBlank @Size(max = 150) String title,
            @Min(0) @Max(1440) Integer estimatedTime,
            @Size(max = 300) @Pattern(regexp = "(https?://.*)?", message = "must be an http(s) URL") String youtubeUrl,
            @NotBlank @Size(max = 100000) String content) {}

    public record ExerciseRequest(@NotBlank @Size(max = 150) String title,
                                  @NotBlank @Size(max = 10000) String description) {}

    public record ToolUpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500) String description,
            @NotBlank @Size(max = 50) String category,
            @Size(max = 300) @Pattern(regexp = "(https?://.*)?", message = "must be an http(s) URL") String logoUrl) {}

    public record OrderRequest(@NotEmpty List<UUID> ids) {}

    private final ContentReloadService reload;
    private final ContentAuthoringService authoring;

    public AdminContentController(ContentReloadService reload, ContentAuthoringService authoring) {
        this.reload = reload;
        this.authoring = authoring;
    }

    @PostMapping("/content/reload")
    ReloadResult reload() {
        return reload.reload();
    }

    @PostMapping("/tools")
    @ResponseStatus(HttpStatus.CREATED)
    Written createTool(@Valid @RequestBody ToolRequest r) {
        return authoring.createTool(r.slug(), r.name(), r.description(), r.category(), r.logoUrl());
    }

    @PostMapping("/tools/{slug}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    Written createModule(@PathVariable String slug, @Valid @RequestBody ModuleRequest r) {
        return authoring.createModule(slug, r.title(), r.description());
    }

    @PostMapping("/modules/{moduleId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    Written createLesson(@PathVariable UUID moduleId, @Valid @RequestBody LessonRequest r) {
        return authoring.createLesson(moduleId, r.title(), r.estimatedTime(), r.youtubeUrl(), r.content());
    }

    @PostMapping("/modules/{moduleId}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    Written createExercise(@PathVariable UUID moduleId, @Valid @RequestBody ExerciseRequest r) {
        return authoring.createExercise(moduleId, r.title(), r.description());
    }

    @PutMapping("/tools/{slug}")
    Written updateTool(@PathVariable String slug, @Valid @RequestBody ToolUpdateRequest r) {
        return authoring.updateTool(slug, r.name(), r.description(), r.category(), r.logoUrl());
    }

    @DeleteMapping("/tools/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTool(@PathVariable String slug) {
        authoring.deleteTool(slug);
    }

    @PutMapping("/tools/{slug}/modules/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorderModules(@PathVariable String slug, @Valid @RequestBody OrderRequest r) {
        authoring.reorderModules(slug, r.ids());
    }

    @PutMapping("/modules/{moduleId}")
    Written updateModule(@PathVariable UUID moduleId, @Valid @RequestBody ModuleRequest r) {
        return authoring.updateModule(moduleId, r.title(), r.description());
    }

    @DeleteMapping("/modules/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteModule(@PathVariable UUID moduleId) {
        authoring.deleteModule(moduleId);
    }

    @PutMapping("/modules/{moduleId}/lessons/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorderLessons(@PathVariable UUID moduleId, @Valid @RequestBody OrderRequest r) {
        authoring.reorderLessons(moduleId, r.ids());
    }

    @PutMapping("/modules/{moduleId}/exercises/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorderExercises(@PathVariable UUID moduleId, @Valid @RequestBody OrderRequest r) {
        authoring.reorderExercises(moduleId, r.ids());
    }

    @PutMapping("/lessons/{lessonId}")
    Written updateLesson(@PathVariable UUID lessonId, @Valid @RequestBody LessonRequest r) {
        return authoring.updateLesson(lessonId, r.title(), r.estimatedTime(), r.youtubeUrl(), r.content());
    }

    @DeleteMapping("/lessons/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLesson(@PathVariable UUID lessonId) {
        authoring.deleteLesson(lessonId);
    }

    @PutMapping("/exercises/{exerciseId}")
    Written updateExercise(@PathVariable UUID exerciseId, @Valid @RequestBody ExerciseRequest r) {
        return authoring.updateExercise(exerciseId, r.title(), r.description());
    }

    @DeleteMapping("/exercises/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteExercise(@PathVariable UUID exerciseId) {
        authoring.deleteExercise(exerciseId);
    }
}