package com.project.skillforge.learning;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByModuleIdOrderBySortOrderAscTitleAsc(UUID moduleId);
    List<Lesson> findByModuleIdIn(java.util.Collection<UUID> moduleIds);
}
