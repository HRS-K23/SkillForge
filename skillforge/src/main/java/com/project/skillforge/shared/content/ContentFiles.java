package com.project.skillforge.shared.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/** Small helpers for safely rewriting content files. */
public final class ContentFiles {

    private ContentFiles() {}

    /** Writes via a temp file in the same directory so readers never see a half-written file. */
    public static void writeAtomically(Path file, String text) throws IOException {
        Path tmp = Files.createTempFile(file.getParent(), ".sf-", ".tmp");
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Sets {@code id:} as the first frontmatter key, leaving the rest of the file untouched. */
    public static void ensureId(Path file, UUID id) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String bom = text.startsWith("\uFEFF") ? "\uFEFF" : "";
        boolean crlf = text.contains("\r\n");
        String s = text.substring(bom.length()).replace("\r\n", "\n");
        String idLine = "id: " + id;
        String result;
        int end = s.startsWith("---\n") ? s.indexOf("\n---", 3) : -1;
        if (end > 0) {
            String yaml = Arrays.stream(s.substring(4, end).split("\n", -1))
                    .filter(l -> !l.matches("^id\\s*:.*"))
                    .collect(Collectors.joining("\n"));
            result = "---\n" + idLine + (yaml.isBlank() ? "" : "\n" + yaml) + s.substring(end);
        } else {
            result = "---\n" + idLine + "\n---\n" + s;
        }
        if (crlf) {
            result = result.replace("\n", "\r\n");
        }
        writeAtomically(file, bom + result);
    }
}