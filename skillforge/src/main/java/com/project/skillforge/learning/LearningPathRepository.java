package com.project.skillforge.learning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningPathRepository extends JpaRepository<LearningPath, UUID> {
    Optional<LearningPath> findByToolId(UUID toolId);
}
