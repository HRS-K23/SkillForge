package com.project.skillforge.learning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {
    List<Exercise> findByModuleIdOrderBySortOrderAscTitleAsc(UUID moduleId);
}
