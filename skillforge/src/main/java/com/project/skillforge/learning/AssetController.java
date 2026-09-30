package com.project.skillforge.learning;

import com.project.skillforge.shared.error.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves images referenced by lessons from content/<tool>/assets/. */
@RestController
@RequestMapping("/api/tools/{slug}/assets")
public class AssetController {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Map<String, MediaType> TYPES = Map.of(
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.parseMediaType("image/webp"));

    private final Path root;

    public AssetController(@Value("${skillforge.content.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @GetMapping("/{filename:.+}")
    ResponseEntity<Resource> asset(@PathVariable String slug, @PathVariable String filename) {
        int dot = filename.lastIndexOf('.');
        MediaType type = dot < 0 ? null : TYPES.get(filename.substring(dot + 1).toLowerCase());
        if (type == null || !SAFE.matcher(slug).matches() || !SAFE.matcher(filename).matches()
                || filename.contains("..")) {
            throw notFound();
        }
        Path file = root.resolve(slug).resolve("assets").resolve(filename).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw notFound();
        }
        return ResponseEntity.ok().contentType(type).header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Asset not found");
    }
}
