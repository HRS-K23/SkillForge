package com.project.skillforge.shared.content;

import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** Splits a Markdown document into YAML frontmatter and body. */
public record FrontMatter(Map<String, Object> data, String body) {

    public static FrontMatter parse(String text) {
        String s = text.startsWith("\uFEFF") ? text.substring(1) : text;
        s = s.replace("\r\n", "\n");
        if (s.startsWith("---\n")) {
            int end = s.indexOf("\n---", 3);
            if (end > 0) {
                String yaml = s.substring(4, end);
                String rest = s.substring(end + 4);
                int nl = rest.indexOf('\n');
                String body = nl < 0 ? "" : rest.substring(nl + 1);
                Object loaded = new Yaml().load(yaml);
                Map<String, Object> map = loaded instanceof Map<?, ?> m ? castMap(m) : Map.of();
                return new FrontMatter(map, body.strip());
            }
        }
        return new FrontMatter(Map.of(), s.strip());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    public String text(String key) {
        Object v = data.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString().trim();
    }

    public Integer integer(String key) {
        Object v = data.get(key);
        if (v == null) {
            return null;
        }
        return v instanceof Number n ? n.intValue() : Integer.valueOf(v.toString().trim());
    }
}
