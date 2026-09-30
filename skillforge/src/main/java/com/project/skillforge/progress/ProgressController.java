package com.project.skillforge.progress;

import com.project.skillforge.progress.ProgressDtos.*;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final ProgressService service;

    public ProgressController(ProgressService service) {
        this.service = service;
    }

    @GetMapping
    List<ToolProgress> overview(Authentication auth) {
        return service.overview(UUID.fromString(auth.getName()));
    }

    @GetMapping("/tools/{slug}")
    ToolProgress tool(Authentication auth, @PathVariable String slug) {
        return service.forTool(UUID.fromString(auth.getName()), slug);
    }

    @PutMapping("/lessons/{lessonId}")
    LessonProgress setCompleted(Authentication auth, @PathVariable UUID lessonId,
                                @RequestBody SetCompletedRequest request) {
        return service.setCompleted(UUID.fromString(auth.getName()), lessonId, request.completed());
    }
}
