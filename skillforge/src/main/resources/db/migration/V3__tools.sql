CREATE TABLE tools (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    category VARCHAR(50) NOT NULL,
    logo_url VARCHAR(500)
);

CREATE UNIQUE INDEX uk_tools_slug ON tools (slug);
CREATE INDEX idx_tools_category ON tools (category);
