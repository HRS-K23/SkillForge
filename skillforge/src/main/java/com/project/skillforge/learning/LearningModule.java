package com.project.skillforge.learning;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "modules")
public class LearningModule {
    @Id private UUID id;
    @Column(name = "learning_path_id", nullable = false) private UUID learningPathId;
    @Column(nullable = false, length = 200) private String folder;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 2000) private String description;
    @Column(name = "sort_order", nullable = false) private int sortOrder;

    protected LearningModule() {}

    public LearningModule(UUID learningPathId, String folder, String title, String description, int sortOrder) {
        this.id = UUID.randomUUID();
        this.learningPathId = learningPathId;
        this.folder = folder;
        update(title, description, sortOrder);
    }

    public void update(String title, String description, int sortOrder) {
        this.title = title;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public UUID getLearningPathId() { return learningPathId; }
    public String getFolder() { return folder; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
}
