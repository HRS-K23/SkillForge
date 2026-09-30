package com.project.skillforge.learning;

import com.project.skillforge.catalog.Tool;
import com.project.skillforge.catalog.ToolRepository;
import com.project.skillforge.shared.content.ContentFiles;
import com.project.skillforge.shared.content.FrontMatter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Upserts learning paths, modules, lessons and exercises from the content directory.
 * Items no longer present on disk are removed. Runs after the tool sync.
 *
 * <p>Lessons are identified by an {@code id} in their frontmatter so that renaming or moving a file
 * keeps the same lesson (and therefore learner progress). Lessons without an id adopt the id of the
 * lesson already stored for the same module and file name, or get a new one, and the id is written
 * back into the file.
 */
@Component
@Order(2)
public class LearningContentSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LearningContentSync.class);
    private static final Pattern MODULE_DIR = Pattern.compile("module-(\\d+)-(.+)");
    private static final Pattern NUMBER = Pattern.compile("(\\d+)");

    private record Candidate(LearningModule module, Path file, String fileName, FrontMatter fm, String contentPath,
                             UUID id, boolean owner) {}

    private final ToolRepository tools;
    private final LearningPathRepository paths;
    private final LearningModuleRepository modules;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final TransactionTemplate tx;
    private final Path root;

    public LearningContentSync(ToolRepository tools, LearningPathRepository paths, LearningModuleRepository modules,
                               LessonRepository lessons, ExerciseRepository exercises,
                               PlatformTransactionManager transactionManager,
                               @Value("${skillforge.content.root}") String root) {
        this.tools = tools;
        this.paths = paths;
        this.modules = modules;
        this.lessons = lessons;
        this.exercises = exercises;
        this.tx = new TransactionTemplate(transactionManager);
        this.root = Path.of(root);
    }

    @Override
    public void run(ApplicationArguments args) {
        sync();
    }

    public void sync() {
        if (!Files.isDirectory(root)) {
            return;
        }
        for (Tool tool : tools.findAll()) {
            Path dir = root.resolve(tool.getSlug());
            if (Files.isDirectory(dir)) {
                try {
                    tx.executeWithoutResult(status -> {
                        try {
                            syncTool(tool, dir);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
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

        List<Path> moduleDirs;
        try (Stream<Path> s = Files.list(dir)) {
            moduleDirs = s.filter(Files::isDirectory).filter(p -> MODULE_DIR.matcher(name(p)).matches()).sorted()
                    .toList();
        }
        Map<String, LearningModule> byFolder = new HashMap<>();
        for (LearningModule m : modules.findByLearningPathIdOrderBySortOrder(path.getId())) {
            byFolder.put(m.getFolder(), m);
        }

        Set<String> seenFolders = new HashSet<>();
        Map<Path, LearningModule> current = new LinkedHashMap<>();
        for (Path moduleDir : moduleDirs) {
            seenFolders.add(name(moduleDir));
            try {
                current.put(moduleDir, upsertModule(path, byFolder, moduleDir));
            } catch (Exception e) {
                log.warn("Skipping module {}: {}", moduleDir, e.getMessage());
            }
        }
        modules.flush();

        List<LearningModule> allModules = modules.findByLearningPathIdOrderBySortOrder(path.getId());
        Map<UUID, Lesson> existingLessons = new HashMap<>();
        if (!allModules.isEmpty()) {
            lessons.findByModuleIdIn(allModules.stream().map(LearningModule::getId).toList())
                    .forEach(l -> existingLessons.put(l.getId(), l));
        }
        reconcileLessons(tool.getSlug(), current, existingLessons);

        for (Map.Entry<Path, LearningModule> e : current.entrySet()) {
            try {
                syncExercises(e.getValue(), markdownFiles(e.getKey()));
            } catch (Exception ex) {
                log.warn("Skipping exercises in {}: {}", e.getKey(), ex.getMessage());
            }
        }

        lessons.flush();
        modules.deleteAll(allModules.stream().filter(m -> !seenFolders.contains(m.getFolder())).toList());
    }

    private LearningModule upsertModule(LearningPath path, Map<String, LearningModule> byFolder, Path moduleDir)
            throws IOException {
        String folder = name(moduleDir);
        Matcher m = MODULE_DIR.matcher(folder);
        m.matches();
        FrontMatter meta = readYaml(moduleDir.resolve("module.yml"));
        Integer declared = meta.integer("order");
        int order = declared != null ? declared : Integer.parseInt(m.group(1));
        String title = orDefault(meta.text("title"), humanize(m.group(2)));
        String description = meta.text("description");

        LearningModule module = byFolder.get(folder);
        if (module == null) {
            module = modules.save(new LearningModule(path.getId(), folder, title, description, order));
            byFolder.put(folder, module);
        } else {
            module.update(title, description, order);
        }
        return module;
    }

    private void reconcileLessons(String slug, Map<Path, LearningModule> current, Map<UUID, Lesson> existing) {
        Map<String, Lesson> byLocation = new HashMap<>();
        existing.values().forEach(l -> byLocation.put(l.getModuleId() + "/" + l.getFileName(), l));

        Set<UUID> protectedIds = new HashSet<>();
        Set<UUID> currentModuleIds = new HashSet<>();
        current.values().forEach(m -> currentModuleIds.add(m.getId()));
        existing.values().stream().filter(l -> !currentModuleIds.contains(l.getModuleId()))
                .forEach(l -> protectedIds.add(l.getId()));
        List<Candidate> raw = new ArrayList<>();
        for (Map.Entry<Path, LearningModule> e : current.entrySet()) {
            LearningModule module = e.getValue();
            List<Path> files;
            try {
                files = markdownFiles(e.getKey());
            } catch (IOException ex) {
                log.warn("Cannot list {}: {}", e.getKey(), ex.getMessage());
                existing.values().stream().filter(l -> l.getModuleId().equals(module.getId()))
                        .forEach(l -> protectedIds.add(l.getId()));
                continue;
            }
            for (Path file : files) {
                String fileName = name(file);
                if (!fileName.startsWith("lesson")) {
                    continue;
                }
                Lesson atLocation = byLocation.get(module.getId() + "/" + fileName);
                try {
                    FrontMatter fm = FrontMatter.parse(Files.readString(file));
                    validateLesson(fm);
                    UUID id = parseId(fm.text("id"));
                    boolean owner = id != null && atLocation != null && atLocation.getId().equals(id);
                    raw.add(new Candidate(module, file, fileName, fm, slug + "/" + module.getFolder() + "/" + fileName,
                            id, owner));
                } catch (Exception ex) {
                    log.warn("Skipping lesson {}: {}", file, ex.getMessage());
                    if (atLocation != null) {
                        protectedIds.add(atLocation.getId());
                    }
                }
            }
        }

        raw.sort(Comparator.comparing((Candidate c) -> !c.owner()));
        Set<UUID> claimed = new HashSet<>();
        List<Candidate> candidates = new ArrayList<>();
        for (Candidate c : raw) {
            UUID id = c.id();
            boolean foreign = id != null && !existing.containsKey(id) && lessons.existsById(id);
            if (id != null && (foreign || !claimed.add(id))) {
                log.warn("Lesson {} repeats id {}; assigning a new id", c.file(), id);
                id = null;
            }
            if (id == null) {
                Lesson atLocation = byLocation.get(c.module().getId() + "/" + c.fileName());
                id = atLocation != null && !claimed.contains(atLocation.getId()) ? atLocation.getId()
                        : UUID.randomUUID();
                claimed.add(id);
            }
            if (!id.equals(c.id())) {
                try {
                    ContentFiles.ensureId(c.file(), id);
                } catch (IOException ex) {
                    log.warn("Could not write id into {} (read-only?): {}", c.file(), ex.getMessage());
                }
            }
            candidates.add(new Candidate(c.module(), c.file(), c.fileName(), c.fm(), c.contentPath(), id, c.owner()));
        }

        List<Lesson> stale = existing.values().stream()
                .filter(l -> !claimed.contains(l.getId()) && !protectedIds.contains(l.getId())).toList();
        if (!stale.isEmpty()) {
            lessons.deleteAll(stale);
            lessons.flush();
        }

        // Park moved lessons on a unique temporary name so swaps cannot hit the (module, file) constraint.
        List<Lesson> moved = new ArrayList<>();
        for (Candidate c : candidates) {
            Lesson l = existing.get(c.id());
            if (l != null && (!l.getModuleId().equals(c.module().getId()) || !l.getFileName().equals(c.fileName()))) {
                l.relocate(l.getModuleId(), "~" + l.getId(), l.getContentPath());
                moved.add(l);
            }
        }
        if (!moved.isEmpty()) {
            lessons.saveAllAndFlush(moved);
        }

        for (Candidate c : candidates) {
            FrontMatter fm = c.fm();
            Integer order = fm.integer("order");
            String body = fm.body().length() > 100000 ? fm.body().substring(0, 100000) : fm.body();
            String title = orDefault(fm.text("title"), humanize(stripExt(c.fileName())));
            int sort = order != null ? order : firstNumber(c.fileName());
            Lesson lesson = existing.get(c.id());
            if (lesson == null) {
                lesson = new Lesson(c.id(), c.module().getId(), c.fileName(), c.contentPath());
            } else {
                lesson.relocate(c.module().getId(), c.fileName(), c.contentPath());
            }
            lesson.update(title, fm.integer("estimatedTime"), fm.text("youtubeUrl"), sort, body);
            lessons.save(lesson);
        }
    }

    private static void validateLesson(FrontMatter fm) {
        Integer time = fm.integer("estimatedTime");
        if (time != null && time < 0) {
            throw new IllegalArgumentException("estimatedTime must not be negative");
        }
        String youtube = fm.text("youtubeUrl");
        if (youtube != null && !youtube.startsWith("http")) {
            throw new IllegalArgumentException("youtubeUrl must be an http(s) URL");
        }
        fm.integer("order");
    }

    private static UUID parseId(String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void syncExercises(LearningModule module, List<Path> files) {
        List<Exercise> existing = exercises.findByModuleIdOrderBySortOrderAscTitleAsc(module.getId());
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
                Integer declared = fm.integer("order");
                int sort = declared != null ? declared : firstNumber(fileName);
                Exercise exercise = existing.stream().filter(e -> e.getFileName().equals(fileName)).findFirst()
                        .orElse(null);
                if (exercise == null) {
                    exercise = new Exercise(module.getId(), fileName);
                    exercise.update(title, fm.body(), sort);
                    exercises.save(exercise);
                } else {
                    exercise.update(title, fm.body(), sort);
                }
                seen.add(fileName);
            } catch (Exception e) {
                log.warn("Skipping exercise {}: {}", file, e.getMessage());
            }
        }
        exercises.deleteAll(existing.stream().filter(e -> !seen.contains(e.getFileName())).toList());
    }

    private static List<Path> markdownFiles(Path moduleDir) throws IOException {
        try (Stream<Path> s = Files.list(moduleDir)) {
            return s.filter(Files::isRegularFile).filter(p -> name(p).endsWith(".md")).sorted().toList();
        }
    }

    private static FrontMatter readYaml(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return new FrontMatter(Map.of(), "");
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
