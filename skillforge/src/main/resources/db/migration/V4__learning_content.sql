CREATE TABLE learning_paths (
    id UUID PRIMARY KEY,
    tool_id UUID NOT NULL REFERENCES tools (id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL
);
CREATE UNIQUE INDEX uk_learning_paths_tool ON learning_paths (tool_id);

CREATE TABLE modules (
    id UUID PRIMARY KEY,
    learning_path_id UUID NOT NULL REFERENCES learning_paths (id) ON DELETE CASCADE,
    folder VARCHAR(200) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    sort_order INT NOT NULL
);
CREATE UNIQUE INDEX uk_modules_path_folder ON modules (learning_path_id, folder);

CREATE TABLE lessons (
    id UUID PRIMARY KEY,
    module_id UUID NOT NULL REFERENCES modules (id) ON DELETE CASCADE,
    file_name VARCHAR(200) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content_path VARCHAR(500) NOT NULL,
    estimated_time INT,
    youtube_url VARCHAR(500),
    sort_order INT NOT NULL
);
CREATE UNIQUE INDEX uk_lessons_module_file ON lessons (module_id, file_name);

CREATE TABLE exercises (
    id UUID PRIMARY KEY,
    module_id UUID NOT NULL REFERENCES modules (id) ON DELETE CASCADE,
    file_name VARCHAR(200) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000) NOT NULL
);
CREATE UNIQUE INDEX uk_exercises_module_file ON exercises (module_id, file_name);
