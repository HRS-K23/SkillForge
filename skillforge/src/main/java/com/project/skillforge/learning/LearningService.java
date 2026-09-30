package com.project.skillforge.learning;

import com.project.skillforge.catalog.Tool;
import com.project.skillforge.catalog.ToolRepository;
import com.project.skillforge.learning.LearningDtos.*;
import com.project.skillforge.shared.content.FrontMatter;
import com.project.skillforge.shared.error.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LearningService {

    private static final Pattern YOUTUBE = Pattern.compile(
            "^https?://(?:www\\.|m\\.)?(?:youtube\\.com/watch\\?(?:.*&)?v=|youtu\\.be/|youtube\\.com/embed/)"
                    + "([A-Za-z0-9_-]{6,20}).*$");

    private final ToolRepository tools;
    private final LearningPathRepository paths;
    private final LearningModuleRepository modules;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final Path root;

    public LearningService(ToolRepository tools, LearningPathRepository paths, LearningModuleRepository modules,
                           LessonRepository lessons, ExerciseRepository exercises,
                           @Value("${skillforge.content.root}") String root) {
        this.tools = tools;
        this.paths = paths;
        this.modules = modules;
        this.lessons = lessons;
        this.exercises = exercises;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public LearningPathResponse getPath(String toolSlug) {
        Tool tool = tools.findBySlug(toolSlug).orElseThrow(() -> notFound("TOOL_NOT_FOUND", "Tool not found"));
        LearningPath path = paths.findByToolId(tool.getId())
                .orElseThrow(() -> notFound("LEARNING_PATH_NOT_FOUND", "Learning path not found"));
        List<ModuleResponse> result = modules.findByLearningPathIdOrderBySortOrder(path.getId()).stream()
                .map(m -> new ModuleResponse(m.getId(), m.getTitle(), m.getDescription(), m.getSortOrder(),
                        lessons.findByModuleIdOrderBySortOrderAscTitleAsc(m.getId()).stream()
                                .map(l -> new LessonSummary(l.getId(), l.getTitle(), l.getEstimatedTime(),
                                        l.getSortOrder()))
                                .toList(),
                        exercises.findByModuleIdOrderByTitle(m.getId()).stream()
                                .map(e -> new ExerciseResponse(e.getId(), e.getTitle(), e.getDescription()))
                                .toList()))
                .toList();
        return new LearningPathResponse(path.getId(), tool.getSlug(), path.getTitle(), path.getDescription(), result);
    }

    public LessonResponse getLesson(UUID id) {
        Lesson lesson = lessons.findById(id).orElseThrow(() -> notFound("LESSON_NOT_FOUND", "Lesson not found"));
        Path file = root.resolve(lesson.getContentPath()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw notFound("LESSON_CONTENT_NOT_FOUND", "Lesson content not found");
        }
        String body;
        try {
            body = FrontMatter.parse(Files.readString(file)).body();
        } catch (IOException e) {
            throw notFound("LESSON_CONTENT_NOT_FOUND", "Lesson content not found");
        }
        String toolSlug = modules.findById(lesson.getModuleId())
                .flatMap(m -> paths.findById(m.getLearningPathId()))
                .flatMap(p -> tools.findById(p.getToolId()))
                .map(Tool::getSlug).orElse(null);
        return new LessonResponse(lesson.getId(), lesson.getModuleId(), toolSlug, lesson.getTitle(),
                lesson.getEstimatedTime(), lesson.getYoutubeUrl(), embedUrl(lesson.getYoutubeUrl()), body);
    }

    static String embedUrl(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = YOUTUBE.matcher(url);
        return m.matches() ? "https://www.youtube.com/embed/" + m.group(1) : null;
    }

    private static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}

