package com.project.skillforge.learning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningModuleRepository extends JpaRepository<LearningModule, UUID> {
    List<LearningModule> findByLearningPathIdOrderBySortOrder(UUID learningPathId);
}
