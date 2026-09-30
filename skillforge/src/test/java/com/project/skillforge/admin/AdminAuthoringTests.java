package com.project.skillforge.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "skillforge.admin.emails=author@example.com")
class AdminAuthoringTests {

    static final Path CONTENT = tempDir();

    static Path tempDir() {
        try {
            return Files.createTempDirectory("sf-content");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("skillforge.content.root", () -> CONTENT.toString());
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:authoring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    @Autowired WebApplicationContext ctx;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private String token(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"email\":\"" + email + "\",\"password\":\"password123\"}"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(res);
        m.find();
        return "Bearer " + m.group(1);
    }

    private void send(String token, String url, String json, int expected) throws Exception {
        mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected));
    }

    @Test
    void adminAuthorsAToolEndToEnd() throws Exception {
        String admin = token("author@example.com");
        send(admin, "/api/admin/tools", "{\"slug\":\"Bad Slug\",\"name\":\"x\",\"description\":\"d\",\"category\":\"c\"}", 400);
        send(admin, "/api/admin/tools", "{\"slug\":\"demo\",\"name\":\"Demo: Tool\",\"description\":\"A demo\","
                + "\"category\":\"Development\"}", 201);
        send(admin, "/api/admin/tools", "{\"slug\":\"demo\",\"name\":\"Demo\",\"description\":\"A\","
                + "\"category\":\"Development\"}", 409);
        send(admin, "/api/admin/tools/demo/modules", "{\"title\":\"Getting Started!\",\"description\":\"Basics\"}", 201);
        send(admin, "/api/admin/tools/missing/modules", "{\"title\":\"X\"}", 404);

        String path = mvc.perform(get("/api/tools/demo/learning-path")).andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(1))
                .andExpect(jsonPath("$.modules[0].title").value("Getting Started!"))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"modules\":\\[\\{\"id\":\"([^\"]+)\"").matcher(path);
        m.find();
        String moduleId = m.group(1);

        send(admin, "/api/admin/modules/" + moduleId + "/lessons", "{\"title\":\"First: lesson\",\"estimatedTime\":5,"
                + "\"youtubeUrl\":\"https://youtu.be/abc\",\"content\":\"# Hello\\n\\nBody text\"}", 201);
        send(admin, "/api/admin/modules/" + moduleId + "/lessons", "{\"title\":\"Bad\",\"youtubeUrl\":\"javascript:x\","
                + "\"content\":\"b\"}", 400);
        send(admin, "/api/admin/modules/" + moduleId + "/exercises", "{\"title\":\"Try it\","
                + "\"description\":\"Create a thing\"}", 201);

        mvc.perform(get("/api/tools/demo/learning-path")).andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[0].lessons.length()").value(1))
                .andExpect(jsonPath("$.modules[0].lessons[0].title").value("First: lesson"))
                .andExpect(jsonPath("$.modules[0].lessons[0].estimatedTime").value(5))
                .andExpect(jsonPath("$.modules[0].exercises[0].title").value("Try it"));
        assertTrue(Files.exists(CONTENT.resolve("demo/module-1-getting-started/lesson-1.md")));
        assertTrue(Files.exists(CONTENT.resolve("demo/module-1-getting-started/exercise-2.md")));
    }

    @Test
    void learnersCannotAuthor() throws Exception {
        send(token("plain@example.com"), "/api/admin/tools", "{\"slug\":\"x\",\"name\":\"x\",\"description\":\"d\","
                + "\"category\":\"c\"}", 403);
    }

    @Test
    void lastAdministratorCannotDeleteTheirAccount() throws Exception {
        String admin = token("author@example.com");
        mvc.perform(delete("/api/users/me").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
    }
}