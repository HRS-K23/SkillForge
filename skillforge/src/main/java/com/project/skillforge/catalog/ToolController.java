package com.project.skillforge.catalog;

import com.project.skillforge.shared.web.PageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolService service;

    public ToolController(ToolService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<ToolResponse> list(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "12") int size) {
        return service.list(q, category, page, size);
    }

    @GetMapping("/categories")
    List<String> categories() {
        return service.categories();
    }

    @GetMapping("/{slug}")
    ToolResponse get(@PathVariable String slug) {
        return service.get(slug);
    }
}
