package com.project.skillforge.learning;

import com.project.skillforge.learning.LearningDtos.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class LearningController {

    private final LearningService service;

    public LearningController(LearningService service) {
        this.service = service;
    }

    @GetMapping("/tools/{slug}/learning-path")
    LearningPathResponse path(@PathVariable String slug) {
        return service.getPath(slug);
    }

    @GetMapping("/lessons/{id}")
    LessonResponse lesson(@PathVariable UUID id) {
        return service.getLesson(id);
    }
}
