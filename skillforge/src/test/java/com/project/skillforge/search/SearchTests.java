package com.project.skillforge.search;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "skillforge.content.root=src/test/resources/content")
class SearchTests {

    @Autowired WebApplicationContext ctx;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void findsToolsPathsAndLessonsPublicly() throws Exception {
        mvc.perform(get("/api/search").param("q", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("alpha"))
                .andExpect(jsonPath("$.results[?(@.type=='TOOL')].toolSlug").value("alpha"))
                .andExpect(jsonPath("$.results[?(@.type=='LEARNING_PATH')].title").value("Alpha Learning Path"))
                .andExpect(jsonPath("$.results[?(@.type=='LESSON')].title").value("What is Alpha?"));
    }

    @Test
    void searchesLessonBodyAndFiltersByType() throws Exception {
        mvc.perform(get("/api/search").param("q", "Only lesson").param("type", "LESSON"))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].type").value("LESSON"))
                .andExpect(jsonPath("$.results[0].snippet").value("Only lesson."));
        mvc.perform(get("/api/search").param("q", "Only lesson").param("type", "TOOL"))
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void rejectsBadQueries() throws Exception {
        mvc.perform(get("/api/search")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/search").param("q", "a")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", "x".repeat(101))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search").param("q", "alpha").param("type", "BOGUS")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/search").param("q", "%%")).andExpect(jsonPath("$.results.length()").value(0));
    }
}
