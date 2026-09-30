package com.project.skillforge.learning;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "skillforge.content.root=src/test/resources/content")
class LearningContentTests {

    @Autowired WebApplicationContext ctx;
    @Autowired LessonRepository lessons;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void exposesLearningPathWithModulesLessonsAndExercises() throws Exception {
        mvc.perform(get("/api/tools/alpha/learning-path"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Alpha Learning Path"))
                .andExpect(jsonPath("$.modules.length()").value(2))
                .andExpect(jsonPath("$.modules[0].title").value("Start Here"))
                .andExpect(jsonPath("$.modules[0].description").value("First steps"))
                .andExpect(jsonPath("$.modules[0].lessons.length()").value(2))
                .andExpect(jsonPath("$.modules[0].lessons[0].title").value("What is Alpha?"))
                .andExpect(jsonPath("$.modules[0].lessons[0].estimatedTime").value(10))
                .andExpect(jsonPath("$.modules[0].exercises[0].description")
                        .value("Create a repository called practice-project"))
                .andExpect(jsonPath("$.modules[1].title").value("Getting Started"));
    }

    @Test
    void returnsLessonMarkdownAndYoutubeEmbed() throws Exception {
        String id = lessons.findAll().stream().filter(l -> l.getTitle().equals("What is Alpha?"))
                .findFirst().orElseThrow().getId().toString();
        mvc.perform(get("/api/lessons/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolSlug").value("alpha"))
                .andExpect(jsonPath("$.youtubeEmbedUrl").value("https://www.youtube.com/embed/abc123XYZ"))
                .andExpect(jsonPath("$.content").value(startsWith("# What is Alpha?")))
                .andExpect(jsonPath("$.content").value(not(containsString("estimatedTime"))));
        mvc.perform(get("/api/lessons/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LESSON_NOT_FOUND"));
    }

    @Test
    void invalidLessonIsSkippedAndUnknownPathIs404() throws Exception {
        assertEquals(0, lessons.findAll().stream().filter(l -> l.getTitle().equals("Bad")).count());
        mvc.perform(get("/api/tools/nope/learning-path")).andExpect(status().isNotFound());
    }

    @Test
    void servesAssetsSafely() throws Exception {
        mvc.perform(get("/api/tools/alpha/assets/pic.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/tools/alpha/assets/missing.png")).andExpect(status().isNotFound());
        mvc.perform(get("/api/tools/alpha/assets/metadata.yml")).andExpect(status().isNotFound());
    }

    @Test
    void embedUrlParsing() {
        assertEquals("https://www.youtube.com/embed/abc123XYZ",
                LearningService.embedUrl("https://youtu.be/abc123XYZ"));
        assertNull(LearningService.embedUrl("https://example.com/video"));
        assertNull(LearningService.embedUrl(null));
    }
}

