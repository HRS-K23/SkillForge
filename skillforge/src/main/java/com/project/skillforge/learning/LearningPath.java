package com.project.skillforge.learning;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "learning_paths")
public class LearningPath {
    @Id private UUID id;
    @Column(name = "tool_id", nullable = false, unique = true) private UUID toolId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 2000) private String description;

    protected LearningPath() {}

    public LearningPath(UUID toolId, String title, String description) {
        this.id = UUID.randomUUID();
        this.toolId = toolId;
        update(title, description);
    }

    public void update(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public UUID getId() { return id; }
    public UUID getToolId() { return toolId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
}
