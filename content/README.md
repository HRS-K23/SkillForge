# Content conventions

Content lives in `content/` (Git-managed Markdown), read by the platform. Admins can also author content from the web UI (/admin), which writes these same files; commit them with Git afterwards.

```text
content/<tool-slug>/metadata.yml          # name, description, category, logoUrl
content/<tool-slug>/module-<n>-<name>/lesson-<n>.md
content/<tool-slug>/module-<n>-<name>/exercise.md
```

Lesson frontmatter: `title`, `order`, `estimatedTime` (minutes), optional `youtubeUrl`.
Module order comes from the `module-<n>` folder prefix.
