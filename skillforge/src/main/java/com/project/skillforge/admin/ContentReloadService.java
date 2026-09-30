package com.project.skillforge.admin;

import com.project.skillforge.catalog.ToolContentSync;
import com.project.skillforge.catalog.ToolRepository;
import com.project.skillforge.learning.LearningContentSync;
import com.project.skillforge.learning.LessonRepository;
import com.project.skillforge.shared.error.ApiException;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ContentReloadService {

    public record ReloadResult(long tools, long lessons) {}

    private final ToolContentSync toolSync;
    private final LearningContentSync learningSync;
    private final ToolRepository tools;
    private final LessonRepository lessons;
    private final ReentrantLock lock = new ReentrantLock();

    public ContentReloadService(ToolContentSync toolSync, LearningContentSync learningSync, ToolRepository tools,
                                LessonRepository lessons) {
        this.toolSync = toolSync;
        this.learningSync = learningSync;
        this.tools = tools;
        this.lessons = lessons;
    }

    public ReloadResult reload() {
        if (!lock.tryLock()) {
            throw new ApiException(HttpStatus.CONFLICT, "RELOAD_IN_PROGRESS", "A content reload is already running");
        }
        try {
            toolSync.sync();
            learningSync.sync();
            return new ReloadResult(tools.count(), lessons.count());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "RELOAD_FAILED", "Content reload failed");
        } finally {
            lock.unlock();
        }
    }
}
