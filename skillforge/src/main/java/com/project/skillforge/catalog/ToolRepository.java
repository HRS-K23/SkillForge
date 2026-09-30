package com.project.skillforge.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ToolRepository extends JpaRepository<Tool, UUID>, JpaSpecificationExecutor<Tool> {
    Optional<Tool> findBySlug(String slug);

    @Query("select distinct t.category from Tool t order by t.category")
    List<String> findCategories();
}
