package com.project.skillforge.learning;

import com.project.skillforge.catalog.Tool;
import com.project.skillforge.catalog.ToolRepository;
import com.project.skillforge.shared.content.FrontMatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts learning paths, modules, lessons and exercises from the content directory.
 * Items no longer present on disk are removed. Runs after the tool sync.
 */
@Component
@Order(2)
public class LearningContentSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LearningContentSync.class);
    private static final Pattern MODULE_DIR = Pattern.compile("module-(\\d+)-(.+)");
    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    private final ToolRepository tools;
    private final LearningPathRepository paths;
    private final LearningModuleRepository modules;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final Path root;

    public LearningContentSync(ToolRepository tools, LearningPathRepository paths, LearningModuleRepository modules,
                               LessonRepository lessons, ExerciseRepository exercises,
                               @Value("${skillforge.content.root}") String root) {
        this.tools = tools;
        this.paths = paths;
        this.modules = modules;
        this.lessons = lessons;
        this.exercises = exercises;
        this.root = Path.of(root);
    }

    @Override
    public void run(ApplicationArguments args) {
        sync();
    }

    @Transactional
    public void sync() {
        if (!Files.isDirectory(root)) {
            return;
        }
        for (Tool tool : tools.findAll()) {
            Path dir = root.resolve(tool.getSlug());
            if (Files.isDirectory(dir)) {
                try {
                    syncTool(tool, dir);
                } catch (Exception e) {
                    log.warn("Skipping learning content for {}: {}", tool.getSlug(), e.getMessage());
                }
            }
        }
    }

    private void syncTool(Tool tool, Path dir) throws IOException {
        FrontMatter meta = readYaml(dir.resolve("metadata.yml"));
        String title = orDefault(meta.text("pathTitle"), tool.getName() + " Learning Path");
        String description = orDefault(meta.text("pathDescription"), tool.getDescription());
        LearningPath path = paths.findByToolId(tool.getId()).orElse(null);
        if (path == null) {
            path = paths.save(new LearningPath(tool.getId(), title, description));
        } else {
            path.update(title, description);
        }

        Set<String> seenFolders = new HashSet<>();
        List<Path> moduleDirs;
        try (Stream<Path> s = Files.list(dir)) {
            moduleDirs = s.filter(Files::isDirectory).filter(p -> MODULE_DIR.matcher(name(p)).matches()).toList();
        }
        List<LearningModule> existing = modules.findByLearningPathIdOrderBySortOrder(path.getId());
        for (Path moduleDir : moduleDirs) {
            String folder = name(moduleDir);
            seenFolders.add(folder);
            try {
                syncModule(path, existing, tool.getSlug(), moduleDir);
            } catch (Exception e) {
                log.warn("Skipping module {}: {}", moduleDir, e.getMessage());
            }
        }
        modules.deleteAll(existing.stream().filter(m -> !seenFolders.contains(m.getFolder())).toList());
    }

    private void syncModule(LearningPath path, List<LearningModule> existing, String slug, Path moduleDir)
            throws IOException {
        String folder = name(moduleDir);
        Matcher m = MODULE_DIR.matcher(folder);
        m.matches();
        int order = Integer.parseInt(m.group(1));
        FrontMatter meta = readYaml(moduleDir.resolve("module.yml"));
        String title = orDefault(meta.text("title"), humanize(m.group(2)));
        String description = meta.text("description");

        LearningModule module = existing.stream().filter(e -> e.getFolder().equals(folder)).findFirst().orElse(null);
        if (module == null) {
            module = modules.save(new LearningModule(path.getId(), folder, title, description, order));
        } else {
            module.update(title, description, order);
        }

        List<Path> files;
        try (Stream<Path> s = Files.list(moduleDir)) {
            files = s.filter(Files::isRegularFile).filter(p -> name(p).endsWith(".md")).sorted().toList();
        }
        syncLessons(module, slug, files);
        syncExercises(module, files);
    }

    private void syncLessons(LearningModule module, String slug, List<Path> files) {
        List<Lesson> existing = lessons.findByModuleIdOrderBySortOrderAscTitleAsc(module.getId());
        Set<String> seen = new HashSet<>();
        for (Path file : files) {
            String fileName = name(file);
            if (!fileName.startsWith("lesson")) {
                continue;
            }
            try {
                FrontMatter fm = FrontMatter.parse(Files.readString(file));
                Integer order = fm.integer("order");
                Integer time = fm.integer("estimatedTime");
                if (time != null && time < 0) {
                    throw new IllegalArgumentException("estimatedTime must not be negative");
                }
                String youtube = fm.text("youtubeUrl");
                if (youtube != null && !youtube.startsWith("http")) {
                    throw new IllegalArgumentException("youtubeUrl must be an http(s) URL");
                }
                String title = orDefault(fm.text("title"), humanize(stripExt(fileName)));
                String searchText = fm.body().length() > 100000 ? fm.body().substring(0, 100000) : fm.body();
                int sort = order != null ? order : firstNumber(fileName);
                Lesson lesson = existing.stream().filter(l -> l.getFileName().equals(fileName)).findFirst()
                        .orElse(null);
                if (lesson == null) {
                    lesson = new Lesson(module.getId(), fileName, slug + "/" + module.getFolder() + "/" + fileName);
                    lesson.update(title, time, youtube, sort, searchText);
                    lessons.save(lesson);
                } else {
                    lesson.update(title, time, youtube, sort, searchText);
                }
                seen.add(fileName);
            } catch (Exception e) {
                log.warn("Skipping lesson {}: {}", file, e.getMessage());
            }
        }
        lessons.deleteAll(existing.stream().filter(l -> !seen.contains(l.getFileName())).toList());
    }

    private void syncExercises(LearningModule module, List<Path> files) {
        List<Exercise> existing = exercises.findByModuleIdOrderByTitle(module.getId());
        Set<String> seen = new HashSet<>();
        for (Path file : files) {
            String fileName = name(file);
            if (!fileName.startsWith("exercise")) {
                continue;
            }
            try {
                FrontMatter fm = FrontMatter.parse(Files.readString(file));
                if (fm.body().isBlank()) {
                    throw new IllegalArgumentException("exercise body is empty");
                }
                String title = orDefault(fm.text("title"), humanize(stripExt(fileName)));
                Exercise exercise = existing.stream().filter(e -> e.getFileName().equals(fileName)).findFirst()
                        .orElse(null);
                if (exercise == null) {
                    exercise = new Exercise(module.getId(), fileName);
                    exercise.update(title, fm.body());
                    exercises.save(exercise);
                } else {
                    exercise.update(title, fm.body());
                }
                seen.add(fileName);
            } catch (Exception e) {
                log.warn("Skipping exercise {}: {}", file, e.getMessage());
            }
        }
        exercises.deleteAll(existing.stream().filter(e -> !seen.contains(e.getFileName())).toList());
    }

    private static FrontMatter readYaml(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new FrontMatter(java.util.Map.of(), "");
        }
        return FrontMatter.parse("---\n" + Files.readString(file) + "\n---\n");
    }

    private static String name(Path p) {
        return p.getFileName().toString();
    }

    private static String stripExt(String f) {
        return f.substring(0, f.length() - 3);
    }

    private static String orDefault(String v, String d) {
        return v != null ? v : d;
    }

    private static int firstNumber(String s) {
        Matcher m = NUMBER.matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    static String humanize(String s) {
        String[] words = s.replace('-', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}


