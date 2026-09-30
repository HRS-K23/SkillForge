package com.project.skillforge.catalog;

import com.project.skillforge.shared.error.ApiException;
import com.project.skillforge.shared.web.PageResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ToolService {

    private final ToolRepository tools;

    public ToolService(ToolRepository tools) {
        this.tools = tools;
    }

    public PageResponse<ToolResponse> list(String query, String category, int page, int size) {
        Specification<Tool> spec = Specification.unrestricted();
        if (category != null && !category.isBlank()) {
            String c = category.trim().toLowerCase();
            spec = spec.and((root, q, cb) -> cb.equal(cb.lower(root.get("category")), c));
        }
        if (query != null && !query.isBlank()) {
            String like = "%" + escape(query.trim().toLowerCase()) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like, '\\'),
                    cb.like(cb.lower(root.get("description")), like, '\\')));
        }
        return PageResponse.from(tools.findAll(spec,
                PageRequest.of(Math.max(page, 0), PageResponse.clampSize(size, 50), Sort.by("name")))
                .map(ToolResponse::from));
    }

    public ToolResponse get(String slug) {
        return tools.findBySlug(slug).map(ToolResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Tool not found"));
    }

    public List<String> categories() {
        return tools.findCategories();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
