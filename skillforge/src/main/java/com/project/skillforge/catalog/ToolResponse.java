package com.project.skillforge.catalog;

import java.util.UUID;

public record ToolResponse(UUID id, String name, String slug, String description, String category, String logoUrl) {
    static ToolResponse from(Tool t) {
        return new ToolResponse(t.getId(), t.getName(), t.getSlug(), t.getDescription(), t.getCategory(),
                t.getLogoUrl());
    }
}
