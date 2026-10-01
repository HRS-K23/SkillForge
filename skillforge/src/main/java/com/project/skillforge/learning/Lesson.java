package com.project.skillforge.learning;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "lessons")
public class Lesson {
    @Id private UUID id;
    @Column(name = "module_id", nullable = false) private UUID moduleId;
    @Column(name = "file_name", nullable = false, length = 200) private String fileName;
    @Column(nullable = false, length = 200) private String title;
    @Column(name = "content_path", nullable = false, length = 500) private String contentPath;
    @Column(name = "estimated_time") private Integer estimatedTime;
    @Column(name = "youtube_url", length = 500) private String youtubeUrl;
    @Column(name = "search_text", length = 100000) private String searchText;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected Lesson() {}

    public Lesson(UUID id, UUID moduleId, String fileName, String contentPath) {
        this.id = id;
        relocate(moduleId, fileName, contentPath);
    }

    /** Points the lesson at a new file location while keeping its identity (and learner progress). */
    public void relocate(UUID moduleId, String fileName, String contentPath) {
        this.moduleId = moduleId;
        this.fileName = fileName;
        this.contentPath = contentPath;
    }

    public void update(String title, Integer estimatedTime, String youtubeUrl, int sortOrder, String searchText) {
        this.searchText = searchText;
        this.title = title;
        this.estimatedTime = estimatedTime;
        this.youtubeUrl = youtubeUrl;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public UUID getModuleId() { return moduleId; }
    public String getFileName() { return fileName; }
    public String getTitle() { return title; }
    public String getContentPath() { return contentPath; }
    public Integer getEstimatedTime() { return estimatedTime; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public int getSortOrder() { return sortOrder; }
}

