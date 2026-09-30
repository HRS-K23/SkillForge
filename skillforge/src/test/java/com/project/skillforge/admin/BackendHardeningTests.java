package com.project.skillforge.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.project.skillforge.learning.LessonRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "skillforge.content.root=src/test/resources/content",
        "skillforge.admin.emails=boss@example.com"})
class BackendHardeningTests {

    @Autowired WebApplicationContext ctx;
    @Autowired LessonRepository lessons;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private String token(String email) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"H\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(res);
        m.find();
        return "Bearer " + m.group(1);
    }

    @Test
    void unauthenticatedAndForbiddenResponsesHaveJsonBodies() throws Exception {
        mvc.perform(get("/api/progress")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/admin/content/reload").header("Authorization", token("learner@example.com")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanReloadContent() throws Exception {
        mvc.perform(post("/api/admin/content/reload").header("Authorization", token("boss@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tools").isNumber())
                .andExpect(jsonPath("$.lessons").isNumber());
    }

    @Test
    void toolsAreSearchedAndPaged() throws Exception {
        mvc.perform(get("/api/tools").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("beta")).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/search").param("q", "alpha").param("size", "1").param("page", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void concurrentFirstCompletionsDoNotFail() throws Exception {
        String token = token("race@example.com");
        UUID lesson = lessons.findAll().get(0).getId();
        int n = 6;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Future<Integer>> results = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<Integer> call = () -> {
                go.await();
                return mvc.perform(put("/api/progress/lessons/" + lesson).header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        go.countDown();
        for (Future<Integer> f : results) {
            assertEquals(200, f.get());
        }
        pool.shutdown();
    }
}
