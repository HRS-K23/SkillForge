-- PostgreSQL-only: GIN indexes matching the expressions used by SearchService.
CREATE INDEX idx_tools_fts ON tools USING GIN (to_tsvector('english', coalesce(name,'') || ' ' || coalesce(description,'')));
CREATE INDEX idx_paths_fts ON learning_paths USING GIN (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,'')));
CREATE INDEX idx_lessons_fts ON lessons USING GIN (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(search_text,'')));
