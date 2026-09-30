package com.project.skillforge.search;

import com.project.skillforge.search.SearchDtos.*;
import com.project.skillforge.shared.error.ApiException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Searches tools, learning paths and lessons. Uses PostgreSQL full-text search when running on
 * PostgreSQL and falls back to a case-insensitive substring match on other databases (tests).
 */
@Service
public class SearchService {

    static final int MAX_SIZE = 50;
    private static final int MIN_QUERY = 2;
    private static final int MAX_QUERY = 100;

    private final JdbcTemplate jdbc;
    private final boolean postgres;

    public SearchService(JdbcTemplate jdbc, DataSource dataSource) throws SQLException {
        this.jdbc = jdbc;
        try (Connection c = dataSource.getConnection()) {
            this.postgres = "PostgreSQL".equalsIgnoreCase(c.getMetaData().getDatabaseProductName());
        }
    }

    public SearchResponse search(String query, Type type, int page, int size) {
        int pageNo = Math.max(page, 0);
        int pageSize = Math.max(1, Math.min(size, MAX_SIZE));
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_QUERY || q.length() > MAX_QUERY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Query must be between " + MIN_QUERY + " and " + MAX_QUERY + " characters");
        }
        List<SearchHit> hits = new ArrayList<>();
        if (type == null || type == Type.TOOL) {
            hits.addAll(tools(q, pageNo, pageSize));
        }
        if (type == null || type == Type.LEARNING_PATH) {
            hits.addAll(paths(q, pageNo, pageSize));
        }
        if (type == null || type == Type.LESSON) {
            hits.addAll(lessons(q, pageNo, pageSize));
        }
        return new SearchResponse(q, pageNo, pageSize, hits);
    }

    private List<SearchHit> tools(String q, int page, int size) {
        String text = "coalesce(t.name,'') || ' ' || coalesce(t.description,'')";
        return run(Type.TOOL,
                "select t.id, t.name as title, t.description as snippet, t.slug as tool_slug",
                "from tools t", text, "t.name", q, page, size);
    }

    private List<SearchHit> paths(String q, int page, int size) {
        String text = "coalesce(p.title,'') || ' ' || coalesce(p.description,'')";
        return run(Type.LEARNING_PATH,
                "select p.id, p.title as title, p.description as snippet, t.slug as tool_slug",
                "from learning_paths p join tools t on t.id = p.tool_id", text, "p.title", q, page, size);
    }

    private List<SearchHit> lessons(String q, int page, int size) {
        String text = "coalesce(l.title,'') || ' ' || coalesce(l.search_text,'')";
        return run(Type.LESSON,
                "select l.id, l.title as title, substring(l.search_text, 1, 200) as snippet, t.slug as tool_slug",
                "from lessons l join modules m on m.id = l.module_id "
                        + "join learning_paths p on p.id = m.learning_path_id join tools t on t.id = p.tool_id",
                text, "l.title", q, page, size);
    }

    private List<SearchHit> run(Type type, String select, String from, String text, String titleCol, String q,
                                 int page, int size) {
        String sql;
        Object[] args;
        if (postgres) {
            String vector = "to_tsvector('english', " + text + ")";
            sql = select + ", ts_rank(" + vector + ", plainto_tsquery('english', ?)) as score " + from
                    + " where " + vector + " @@ plainto_tsquery('english', ?)"
                    + " order by score desc, " + titleCol + " limit " + size + " offset " + (long) page * size;
            args = new Object[] {q, q};
        } else {
            sql = select + " " + from + " where lower(" + text + ") like ? escape '\\'"
                    + " order by " + titleCol + " limit " + size + " offset " + (long) page * size;
            args = new Object[] {"%" + escape(q.toLowerCase()) + "%"};
        }
        RowMapper<SearchHit> mapper = (rs, i) -> new SearchHit(type, rs.getString("id"), rs.getString("title"),
                rs.getString("snippet"), rs.getString("tool_slug"));
        return jdbc.query(sql, mapper, args);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
