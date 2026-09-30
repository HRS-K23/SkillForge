CREATE TABLE user_progress (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    lesson_id UUID NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    completed BOOLEAN NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uk_user_progress_user_lesson ON user_progress (user_id, lesson_id);
