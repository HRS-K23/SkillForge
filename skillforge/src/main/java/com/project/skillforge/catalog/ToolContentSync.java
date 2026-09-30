package com.project.skillforge.catalog;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

/** Upserts tools from content/<slug>/metadata.yml at startup. */
@Component
@Order(1)
public class ToolContentSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolContentSync.class);

    private final ToolRepository tools;
    private final Path root;

    public ToolContentSync(ToolRepository tools, @Value("${skillforge.content.root}") String root) {
        this.tools = tools;
        this.root = Path.of(root);
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        sync();
    }

    @Transactional
    public void sync() throws IOException {
        if (!Files.isDirectory(root)) {
            log.warn("Content root {} not found; skipping tool sync", root.toAbsolutePath());
            return;
        }
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(this::syncTool);
        }
    }

    private void syncTool(Path dir) {
        Path meta = dir.resolve("metadata.yml");
        if (!Files.isRegularFile(meta)) {
            return;
        }
        String slug = dir.getFileName().toString();
        try (Reader reader = Files.newBufferedReader(meta)) {
            Map<String, Object> data = new Yaml().load(reader);
            String name = text(data, "name");
            String description = text(data, "description");
            String category = text(data, "category");
            if (name == null || description == null || category == null) {
                log.warn("Skipping {}: name, description and category are required", meta);
                return;
            }
            String logoUrl = text(data, "logoUrl");
            tools.findBySlug(slug).ifPresentOrElse(
                    t -> t.update(name, description, category, logoUrl),
                    () -> tools.save(new Tool(name, slug, description, category, logoUrl)));
        } catch (Exception e) {
            log.warn("Skipping {}: {}", meta, e.getMessage());
        }
    }

    private static String text(Map<String, Object> data, String key) {
        Object v = data == null ? null : data.get(key);
        return v == null || v.toString().isBlank() ? null : v.toString().trim();
    }
}

