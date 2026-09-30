package com.project.skillforge.admin;

import com.project.skillforge.catalog.Tool;
import com.project.skillforge.catalog.ToolRepository;
import com.project.skillforge.learning.Exercise;
import com.project.skillforge.learning.ExerciseRepository;
import com.project.skillforge.learning.LearningModule;
import com.project.skillforge.learning.LearningModuleRepository;
import com.project.skillforge.learning.LearningPathRepository;
import com.project.skillforge.learning.Lesson;
import com.project.skillforge.learning.LessonRepository;
import com.project.skillforge.shared.content.ContentFiles;
import com.project.skillforge.shared.content.FrontMatter;
import com.project.skillforge.shared.error.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Creates, edits, reorders and deletes content files (tools, modules, lessons, exercises) in the
 * Git-managed content directory and reloads the database from them. Nothing is committed; that stays
 * a manual Git step. Ordering is stored as {@code order} in frontmatter / module.yml, so reordering
 * never renames files or folders and lesson ids (and learner progress) are preserved.
 */
@Service
public class ContentAuthoringService {

    public record Written(String path) {}

    private static final Pattern MODULE_DIR = Pattern.compile("module-(\\d+)-.+");
    private static final Pattern NUMBERED = Pattern.compile("(?:lesson|exercise)-(\\d+)\\.md");

    private final Path root;
    private final ToolRepository tools;
    private final LearningModuleRepository modules;
    private final LearningPathRepository paths;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final ContentReloadService reloader;

    public ContentAuthoringService(@Value("${skillforge.content.root}") String root, ToolRepository tools,
                                   LearningModuleRepository modules, LearningPathRepository paths,
                                   LessonRepository lessons, ExerciseRepository exercises,
                                   ContentReloadService reloader) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.tools = tools;
        this.modules = modules;
        this.paths = paths;
        this.lessons = lessons;
        this.exercises = exercises;
        this.reloader = reloader;
    }

    // ---- tools ----

    public synchronized Written createTool(String slug, String name, String description, String category,
                                           String logoUrl) {
        Path dir = root.resolve(slug);
        if (Files.exists(dir) || tools.findBySlug(slug).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "TOOL_ALREADY_EXISTS", "A tool with this slug already exists");
        }
        write(dir.resolve("metadata.yml"), FrontMatter.dump(toolMeta(new LinkedHashMap<>(), name, description,
                category, logoUrl)));
        reloader.reload();
        return new Written(slug + "/metadata.yml");
    }

    public synchronized Written updateTool(String slug, String name, String description, String category,
                                           String logoUrl) {
        Path file = toolDir(slug).resolve("metadata.yml");
        overwrite(file, FrontMatter.dump(toolMeta(readYaml(file), name, description, category, logoUrl)));
        reloader.reload();
        return new Written(relative(file));
    }

    public synchronized void deleteTool(String slug) {
        Tool tool = tools.findBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Tool not found"));
        deleteTree(contained(root.resolve(slug)));
        tools.delete(tool);
        reloader.reload();
    }

    private static Map<String, Object> toolMeta(Map<String, Object> meta, String name, String description,
                                                String category, String logoUrl) {
        meta.put("name", name.trim());
        meta.put("description", description.trim());
        meta.put("category", category.trim());
        putOrRemove(meta, "logoUrl", blankToNull(logoUrl));
        return meta;
    }

    // ---- modules ----

    public synchronized Written createModule(String slug, String title, String description) {
        Path toolDir = toolDir(slug);
        Tool tool = tools.findBySlug(slug).orElseThrow();
        int order = paths.findByToolId(tool.getId())
                .map(p -> modules.findByLearningPathIdOrderBySortOrder(p.getId()).stream()
                        .mapToInt(LearningModule::getSortOrder).max().orElse(0))
                .orElse(0) + 1;
        String folder = "module-" + (maxNumber(toolDir, MODULE_DIR, true) + 1) + "-" + slugify(title);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", title.trim());
        putOrRemove(meta, "description", blankToNull(description));
        meta.put("order", order);
        write(toolDir.resolve(folder).resolve("module.yml"), FrontMatter.dump(meta));
        reloader.reload();
        return new Written(slug + "/" + folder);
    }

    public synchronized Written updateModule(UUID moduleId, String title, String description) {
        Path file = moduleDir(moduleId).resolve("module.yml");
        Map<String, Object> meta = readYaml(file);
        meta.put("title", title.trim());
        putOrRemove(meta, "description", blankToNull(description));
        overwrite(file, FrontMatter.dump(meta));
        reloader.reload();
        return new Written(relative(file));
    }

    public synchronized void deleteModule(UUID moduleId) {
        deleteTree(moduleDir(moduleId));
        reloader.reload();
    }

    public synchronized void reorderModules(String slug, List<UUID> ids) {
        Tool tool = tools.findBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Tool not found"));
        Path toolDir = toolDir(slug);
        List<LearningModule> current = paths.findByToolId(tool.getId())
                .map(p -> modules.findByLearningPathIdOrderBySortOrder(p.getId())).orElse(List.of());
        requireSameIds(ids, current.stream().map(LearningModule::getId).toList());
        Map<UUID, LearningModule> byId = new LinkedHashMap<>();
        current.forEach(m -> byId.put(m.getId(), m));
        for (int i = 0; i < ids.size(); i++) {
            Path file = contained(toolDir.resolve(byId.get(ids.get(i)).getFolder()).resolve("module.yml"));
            Map<String, Object> meta = readYaml(file);
            meta.put("order", i + 1);
            overwrite(file, FrontMatter.dump(meta));
        }
        reloader.reload();
    }

    // ---- lessons ----

    public synchronized Written createLesson(UUID moduleId, String title, Integer estimatedTime, String youtubeUrl,
                                             String content) {
        Path dir = moduleDir(moduleId);
        int n = maxNumber(dir, NUMBERED, false) + 1;
        int order = lessons.findByModuleIdOrderBySortOrderAscTitleAsc(moduleId).stream()
                .mapToInt(Lesson::getSortOrder).max().orElse(0) + 1;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", UUID.randomUUID().toString());
        meta.put("title", title.trim());
        meta.put("order", order);
        putOrRemove(meta, "estimatedTime", estimatedTime);
        putOrRemove(meta, "youtubeUrl", blankToNull(youtubeUrl));
        String file = "lesson-" + n + ".md";
        write(dir.resolve(file), FrontMatter.render(meta, content));
        reloader.reload();
        return new Written(relative(dir.resolve(file)));
    }

    public synchronized Written updateLesson(UUID lessonId, String title, Integer estimatedTime, String youtubeUrl,
                                             String content) {
        Lesson lesson = lesson(lessonId);
        Path file = lessonFile(lesson);
        Map<String, Object> meta = readFrontMatter(file);
        meta.put("title", title.trim());
        putOrRemove(meta, "estimatedTime", estimatedTime);
        putOrRemove(meta, "youtubeUrl", blankToNull(youtubeUrl));
        overwrite(file, FrontMatter.render(withId(meta, lesson.getId()), content));
        reloader.reload();
        return new Written(relative(file));
    }

    public synchronized void deleteLesson(UUID lessonId) {
        deleteFile(lessonFile(lesson(lessonId)));
        reloader.reload();
    }

    public synchronized void reorderLessons(UUID moduleId, List<UUID> ids) {
        moduleDir(moduleId);
        List<Lesson> current = lessons.findByModuleIdOrderBySortOrderAscTitleAsc(moduleId);
        requireSameIds(ids, current.stream().map(Lesson::getId).toList());
        Map<UUID, Lesson> byId = new LinkedHashMap<>();
        current.forEach(l -> byId.put(l.getId(), l));
        for (int i = 0; i < ids.size(); i++) {
            Lesson lesson = byId.get(ids.get(i));
            Path file = lessonFile(lesson);
            FrontMatter fm = parseFile(file);
            Map<String, Object> meta = new LinkedHashMap<>(fm.data());
            meta.put("order", i + 1);
            overwrite(file, FrontMatter.render(withId(meta, lesson.getId()), fm.body()));
        }
        reloader.reload();
    }

    // ---- exercises ----

    public synchronized Written createExercise(UUID moduleId, String title, String description) {
        Path dir = moduleDir(moduleId);
        int n = maxNumber(dir, NUMBERED, false) + 1;
        int order = exercises.findByModuleIdOrderBySortOrderAscTitleAsc(moduleId).stream()
                .mapToInt(Exercise::getSortOrder).max().orElse(0) + 1;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", title.trim());
        meta.put("order", order);
        String file = "exercise-" + n + ".md";
        write(dir.resolve(file), FrontMatter.render(meta, description));
        reloader.reload();
        return new Written(relative(dir.resolve(file)));
    }

    public synchronized Written updateExercise(UUID exerciseId, String title, String description) {
        Path file = exerciseFile(exercise(exerciseId));
        Map<String, Object> meta = readFrontMatter(file);
        meta.put("title", title.trim());
        overwrite(file, FrontMatter.render(meta, description));
        reloader.reload();
        return new Written(relative(file));
    }

    public synchronized void deleteExercise(UUID exerciseId) {
        deleteFile(exerciseFile(exercise(exerciseId)));
        reloader.reload();
    }

    public synchronized void reorderExercises(UUID moduleId, List<UUID> ids) {
        moduleDir(moduleId);
        List<Exercise> current = exercises.findByModuleIdOrderBySortOrderAscTitleAsc(moduleId);
        requireSameIds(ids, current.stream().map(Exercise::getId).toList());
        Map<UUID, Exercise> byId = new LinkedHashMap<>();
        current.forEach(e -> byId.put(e.getId(), e));
        for (int i = 0; i < ids.size(); i++) {
            Path file = exerciseFile(byId.get(ids.get(i)));
            FrontMatter fm = parseFile(file);
            Map<String, Object> meta = new LinkedHashMap<>(fm.data());
            meta.put("order", i + 1);
            overwrite(file, FrontMatter.render(meta, fm.body()));
        }
        reloader.reload();
    }

    // ---- lookups ----

    private Lesson lesson(UUID id) {
        return lessons.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
    }

    private Exercise exercise(UUID id) {
        return exercises.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EXERCISE_NOT_FOUND", "Exercise not found"));
    }

    private Path lessonFile(Lesson lesson) {
        return existingFile(contained(root.resolve(lesson.getContentPath())));
    }

    private Path exerciseFile(Exercise exercise) {
        return existingFile(contained(moduleDir(exercise.getModuleId()).resolve(exercise.getFileName())));
    }

    private static Path existingFile(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTENT_OUT_OF_SYNC",
                    "The content file is missing on disk; reload content first");
        }
        return file;
    }

    private Path toolDir(String slug) {
        if (tools.findBySlug(slug).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Tool not found");
        }
        return contained(root.resolve(slug));
    }

    private Path moduleDir(UUID moduleId) {
        LearningModule module = modules.findById(moduleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));
        String slug = paths.findById(module.getLearningPathId())
                .flatMap(p -> tools.findById(p.getToolId())).map(Tool::getSlug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Tool not found"));
        Path dir = contained(root.resolve(slug).resolve(module.getFolder()));
        if (!Files.isDirectory(dir)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTENT_OUT_OF_SYNC",
                    "Module folder is missing on disk; reload content first");
        }
        return dir;
    }

    private static void requireSameIds(List<UUID> requested, Collection<UUID> actual) {
        if (requested.size() != actual.size() || !new HashSet<>(requested).equals(new HashSet<>(actual))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER",
                    "The order must list every existing item exactly once");
        }
    }

    // ---- file helpers ----

    private Path contained(Path p) {
        Path n = p.toAbsolutePath().normalize();
        if (!n.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid path");
        }
        return n;
    }

    private String relative(Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    private static int maxNumber(Path dir, Pattern pattern, boolean directories) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> directories ? Files.isDirectory(p) : Files.isRegularFile(p))
                    .map(p -> pattern.matcher(p.getFileName().toString()))
                    .filter(Matcher::matches).mapToInt(m -> Integer.parseInt(m.group(1))).max().orElse(0);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT_WRITE_FAILED", "Cannot read content");
        }
    }

    private static FrontMatter parseFile(Path file) {
        try {
            return FrontMatter.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT_READ_FAILED",
                    "Could not read content file");
        }
    }

    private static Map<String, Object> readFrontMatter(Path file) {
        return new LinkedHashMap<>(parseFile(file).data());
    }

    private static Map<String, Object> readYaml(Path file) {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(
                    FrontMatter.parse("---\n" + Files.readString(file, StandardCharsets.UTF_8) + "\n---\n").data());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT_READ_FAILED",
                    "Could not read content file");
        }
    }

    private static Map<String, Object> withId(Map<String, Object> meta, UUID id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id.toString());
        meta.forEach((k, v) -> {
            if (!k.equals("id")) {
                out.put(k, v);
            }
        });
        return out;
    }

    private static void putOrRemove(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTENT_EXISTS", "Content file already exists");
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT_WRITE_FAILED",
                    "Could not write content file");
        }
    }

    private void overwrite(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            ContentFiles.writeAtomically(file, text);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT_WRITE_FAILED",
                    "Could not write content file");
        }
    }

    private void deleteFile(Path file) {
        try {
            Files.deleteIfExists(contained(file));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT_WRITE_FAILED",
                    "Could not delete content file");
        }
    }

    private void deleteTree(Path dir) {
        Path target = contained(dir);
        if (target.equals(root) || !Files.isDirectory(target)) {
            return;
        }
        try (Stream<Path> s = Files.walk(target)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CONTENT_WRITE_FAILED",
                    "Could not delete content");
        }
    }

    static String slugify(String title) {
        String s = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        s = s.length() > 60 ? s.substring(0, 60).replaceAll("-+$", "") : s;
        return s.isEmpty() ? "module" : s;
    }
}
