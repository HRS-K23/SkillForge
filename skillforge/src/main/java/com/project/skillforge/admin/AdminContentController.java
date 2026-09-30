package com.project.skillforge.admin;

import com.project.skillforge.admin.ContentReloadService.ReloadResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {

    private final ContentReloadService service;

    public AdminContentController(ContentReloadService service) {
        this.service = service;
    }

    @PostMapping("/reload")
    ReloadResult reload() {
        return service.reload();
    }
}
