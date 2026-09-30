package com.project.skillforge.progress;

import com.project.skillforge.catalog.Tool;
import com.project.skillforge.catalog.ToolRepository;
import com.project.skillforge.learning.LearningModule;
import com.project.skillforge.learning.LearningModuleRepository;
import com.project.skillforge.learning.LearningPath;
import com.project.skillforge.learning.LearningPathRepository;
import com.project.skillforge.learning.Lesson;
import com.project.skillforge.learning.LessonRepository;
import com.project.skillforge.progress.ProgressDtos.*;
import com.project.skillforge.shared.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {

    private final UserProgressRepository progress;
    private final LessonRepository lessons;
    private final LearningModuleRepository modules;
    private final LearningPathRepository paths;
    private final ToolRepository tools;
    private final TransactionTemplate tx;

    public ProgressService(UserProgressRepository progress, LessonRepository lessons,
                           LearningModuleRepository modules, LearningPathRepository paths, ToolRepository tools,
                           PlatformTransactionManager txManager) {
        this.progress = progress;
        this.lessons = lessons;
        this.modules = modules;
        this.paths = paths;
        this.tools = tools;
        this.tx = new TransactionTemplate(txManager);
    }

    public LessonProgress setCompleted(UUID userId, UUID lessonId, boolean completed) {
        try {
            return tx.execute(s -> upsert(userId, lessonId, completed));
        } catch (DataIntegrityViolationException e) {
            // A concurrent first completion inserted the row; retry against it.
            return tx.execute(s -> upsert(userId, lessonId, completed));
        }
    }

    private LessonProgress upsert(UUID userId, UUID lessonId, boolean completed) {
        if (!lessons.existsById(lessonId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found");
        }
        UserProgress p = progress.findByUserIdAndLessonId(userId, lessonId)
                .orElseGet(() -> new UserProgress(userId, lessonId));
        p.setCompleted(completed);
        progress.saveAndFlush(p);
        return new LessonProgress(lessonId, p.isCompleted(), p.getCompletedAt());
    }
    @Transactional(readOnly = true)
    public ToolProgress forTool(UUID userId, String slug) {
        Tool tool = tools.findBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Tool not found"));
        LearningPath path = paths.findByToolId(tool.getId()).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "LEARNING_PATH_NOT_FOUND", "Learning path not found"));
        return build(userId, tool, path);
    }

    @Transactional(readOnly = true)
    public List<ToolProgress> overview(UUID userId) {
        List<ToolProgress> result = new ArrayList<>();
        for (UUID pathId : progress.findStartedPathIds(userId)) {
            paths.findById(pathId).flatMap(p -> tools.findById(p.getToolId()).map(t -> build(userId, t, p)))
                    .ifPresent(result::add);
        }
        result.sort((a, b) -> a.toolName().compareToIgnoreCase(b.toolName()));
        return result;
    }

    private ToolProgress build(UUID userId, Tool tool, LearningPath path) {
        List<LearningModule> moduleList = modules.findByLearningPathIdOrderBySortOrder(path.getId());
        Map<UUID, List<Lesson>> lessonsByModule = moduleList.stream().collect(Collectors.toMap(
                LearningModule::getId, m -> lessons.findByModuleIdOrderBySortOrderAscTitleAsc(m.getId())));
        List<UUID> allIds = lessonsByModule.values().stream().flatMap(List::stream).map(Lesson::getId).toList();
        Map<UUID, UserProgress> done = allIds.isEmpty() ? Map.of()
                : progress.findByUserIdAndCompletedTrueAndLessonIdIn(userId, allIds).stream()
                        .collect(Collectors.toMap(UserProgress::getLessonId, Function.identity()));

        List<ModuleProgress> moduleProgress = new ArrayList<>();
        int total = 0;
        int completed = 0;
        for (LearningModule m : moduleList) {
            List<LessonProgress> items = lessonsByModule.get(m.getId()).stream().map(l -> {
                UserProgress p = done.get(l.getId());
                return new LessonProgress(l.getId(), p != null, p == null ? null : p.getCompletedAt());
            }).toList();
            int mDone = (int) items.stream().filter(LessonProgress::completed).count();
            moduleProgress.add(new ModuleProgress(m.getId(), m.getTitle(), items.size(), mDone,
                    percent(mDone, items.size()), items));
            total += items.size();
            completed += mDone;
        }
        return new ToolProgress(tool.getSlug(), tool.getName(), total, completed, percent(completed, total),
                moduleProgress);
    }

    static int percent(int done, int total) {
        return total == 0 ? 0 : Math.round(done * 100f / total);
    }
}
