package com.project.skillforge.progress;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {
    Optional<UserProgress> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    List<UserProgress> findByUserIdAndCompletedTrueAndLessonIdIn(UUID userId, Collection<UUID> lessonIds);

    @Query("select distinct m.learningPathId from LearningModule m, Lesson l, UserProgress p "
            + "where l.moduleId = m.id and p.lessonId = l.id and p.userId = :userId and p.completed = true")
    List<UUID> findStartedPathIds(UUID userId);
}
