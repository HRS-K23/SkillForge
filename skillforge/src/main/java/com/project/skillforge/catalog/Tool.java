package com.project.skillforge.catalog;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tools")
public class Tool {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    protected Tool() {}

    public Tool(String name, String slug, String description, String category, String logoUrl) {
        this.id = UUID.randomUUID();
        this.slug = slug;
        update(name, description, category, logoUrl);
    }

    public void update(String name, String description, String category, String logoUrl) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.logoUrl = logoUrl;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getLogoUrl() { return logoUrl; }
}
