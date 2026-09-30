package com.project.skillforge.learning;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "exercises")
public class Exercise {
    @Id private UUID id;
    @Column(name = "module_id", nullable = false) private UUID moduleId;
    @Column(name = "file_name", nullable = false, length = 200) private String fileName;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 4000) private String description;

    protected Exercise() {}

    public Exercise(UUID moduleId, String fileName) {
        this.id = UUID.randomUUID();
        this.moduleId = moduleId;
        this.fileName = fileName;
    }

    public void update(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public UUID getId() { return id; }
    public UUID getModuleId() { return moduleId; }
    public String getFileName() { return fileName; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
}
