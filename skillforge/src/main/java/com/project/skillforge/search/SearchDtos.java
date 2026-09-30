package com.project.skillforge.search;

import java.util.List;

public final class SearchDtos {
    private SearchDtos() {}

    public enum Type { TOOL, LEARNING_PATH, LESSON }

    public record SearchHit(Type type, String id, String title, String snippet, String toolSlug) {}

    public record SearchResponse(String query, int page, int size, List<SearchHit> results) {}
}
