package com.project.skillforge.catalog;

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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest(properties = "skillforge.content.root=src/test/resources/content")
class ToolCatalogTests {

    @Autowired WebApplicationContext ctx;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void listsToolsPubliclyOrderedByName() throws Exception {
        mvc.perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].slug").value("alpha"))
                .andExpect(jsonPath("$.items[1].slug").value("beta"));
    }

    @Test
    void filtersByCategoryAndSearches() throws Exception {
        mvc.perform(get("/api/tools").param("category", "design"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("beta"));
        mvc.perform(get("/api/tools").param("q", "REPOSITORIES"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("alpha"));
        mvc.perform(get("/api/tools").param("q", "%"))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void categoriesAndDetail() throws Exception {
        mvc.perform(get("/api/tools/categories"))
                .andExpect(jsonPath("$[0]").value("Design"))
                .andExpect(jsonPath("$[1]").value("Development"));
        mvc.perform(get("/api/tools/alpha")).andExpect(jsonPath("$.name").value("Alpha"));
        mvc.perform(get("/api/tools/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOOL_NOT_FOUND"));
    }
}
