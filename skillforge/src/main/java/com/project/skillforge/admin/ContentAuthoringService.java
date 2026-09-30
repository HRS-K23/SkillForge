package com.project.skillforge.admin;

import com.project.skillforge.catalog.ToolRepository;
import com.project.skillforge.learning.LearningModule;
import com.project.skillforge.learning.LearningModuleRepository;
import com.project.skillforge.learning.LearningPathRepository;
import com.project.skillforge.shared.error.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Writes content files (tools, modules, lessons, exercises) into the Git-managed content directory
 * and reloads the database from them. Files are not committed; that stays a manual Git step.
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
    private final ContentReloadService reloader;

    public ContentAuthoringService(@Value("${skillforge.content.root}") String root, ToolRepository tools,
                                   LearningModuleRepository modules, LearningPathRepository paths,
                                   ContentReloadService reloader) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.tools = tools;
        this.modules = modules;
        this.paths = paths;
        this.reloader = reloader;
    }

    public synchronized Written createTool(String slug, String name, String description, String category,
                                           String logoUrl) {
        Path dir = root.resolve(slug);
        if (Files.exists(dir) || tools.findBySlug(slug).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "TOOL_ALREADY_EXISTS", "A tool with this slug already exists");
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", name.trim());
        meta.put("description", description.trim());
        meta.put("category", category.trim());
        if (logoUrl != null && !logoUrl.isBlank()) {
            meta.put("logoUrl", logoUrl.trim());
        }
        write(dir.resolve("metadata.yml"), yaml(meta), true);
        reloader.reload();
        return new Written(slug + "/metadata.yml");
    }

    public synchronized Written createModule(String slug, String title, String description) {
        Path toolDir = toolDir(slug);
        String folder = "module-" + (maxNumber(toolDir, MODULE_DIR, true) + 1) + "-" + slugify(title);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", title.trim());
        if (description != null && !description.isBlank()) {
            meta.put("description", description.trim());
        }
        write(toolDir.resolve(folder).resolve("module.yml"), yaml(meta), true);
        reloader.reload();
        return new Written(slug + "/" + folder);
    }

    public synchronized Written createLesson(UUID moduleId, String title, Integer estimatedTime, String youtubeUrl,
                                             String content) {
        Path dir = moduleDir(moduleId);
        int n = maxNumber(dir, NUMBERED, false) + 1;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", title.trim());
        meta.put("order", n);
        if (estimatedTime != null) {
            meta.put("estimatedTime", estimatedTime);
        }
        if (youtubeUrl != null && !youtubeUrl.isBlank()) {
            meta.put("youtubeUrl", youtubeUrl.trim());
        }
        String file = "lesson-" + n + ".md";
        write(dir.resolve(file), "---\n" + yaml(meta) + "---\n" + content.strip() + "\n", true);
        reloader.reload();
        return new Written(relative(dir.resolve(file)));
    }

    public synchronized Written createExercise(UUID moduleId, String title, String description) {
        Path dir = moduleDir(moduleId);
        int n = maxNumber(dir, NUMBERED, false) + 1;
        String file = "exercise-" + n + ".md";
        write(dir.resolve(file), "---\n" + yaml(Map.of("title", title.trim())) + "---\n" + description.strip() + "\n",
                true);
        reloader.reload();
        return new Written(relative(dir.resolve(file)));
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
                .flatMap(p -> tools.findById(p.getToolId())).map(t -> t.getSlug())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Tool not found"));
        Path dir = contained(root.resolve(slug).resolve(module.getFolder()));
        if (!Files.isDirectory(dir)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTENT_OUT_OF_SYNC",
                    "Module folder is missing on disk; reload content first");
        }
        return dir;
    }

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

    private void write(Path file, String text, boolean createNew) {
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

    private static String yaml(Map<String, Object> map) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setSplitLines(false);
        return new Yaml(options).dump(map);
    }

    static String slugify(String title) {
        String s = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        s = s.length() > 60 ? s.substring(0, 60).replaceAll("-+$", "") : s;
        return s.isEmpty() ? "module" : s;
    }
}
