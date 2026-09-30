package com.project.skillforge.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

@SpringBootTest(properties = "skillforge.admin.emails=editor@example.com")
class ContentEditingTests {

    static final Path CONTENT = tempDir();

    static Path tempDir() {
        try {
            return Files.createTempDirectory("sf-editing");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("skillforge.content.root", () -> CONTENT.toString());
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:editing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
        return "Bearer " + JsonPath.read(res, "$.accessToken");
    }

    private void send(String method, String token, String url, String json, int expected) throws Exception {
        var builder = switch (method) {
            case "PUT" -> put(url);
            case "DELETE" -> delete(url);
            default -> post(url);
        };
        if (json != null) {
            builder = builder.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        mvc.perform(builder.header("Authorization", token)).andExpect(status().is(expected));
    }

    private String path(String slug) throws Exception {
        return mvc.perform(get("/api/tools/" + slug + "/learning-path")).andReturn().getResponse()
                .getContentAsString();
    }

    private List<String> lessonIds(String slug, int module) throws Exception {
        return JsonPath.read(path(slug), "$.modules[" + module + "].lessons[*].id");
    }

    private List<String> lessonTitles(String slug, int module) throws Exception {
        return JsonPath.read(path(slug), "$.modules[" + module + "].lessons[*].title");
    }

    private String ids(List<String> ids) {
        return "{\"ids\":[" + String.join(",", ids.stream().map(i -> "\"" + i + "\"").toList()) + "]}";
    }

    @Test
    void adminEditsReordersAndDeletesContentWithoutLosingProgress() throws Exception {
        String admin = token("editor@example.com");
        String learner = token("learner@example.com");
        send("POST", admin, "/api/admin/tools", "{\"slug\":\"edit\",\"name\":\"Edit\",\"description\":\"d\","
                + "\"category\":\"Development\"}", 201);
        send("POST", admin, "/api/admin/tools/edit/modules", "{\"title\":\"One\"}", 201);
        send("POST", admin, "/api/admin/tools/edit/modules", "{\"title\":\"Two\"}", 201);
        String m1 = JsonPath.read(path("edit"), "$.modules[0].id");
        String m2 = JsonPath.read(path("edit"), "$.modules[1].id");
        for (String t : List.of("A", "B", "C")) {
            send("POST", admin, "/api/admin/modules/" + m1 + "/lessons", "{\"title\":\"" + t + "\",\"content\":\"x\"}", 201);
        }
        send("POST", admin, "/api/admin/modules/" + m1 + "/exercises", "{\"title\":\"E1\",\"description\":\"d\"}", 201);
        send("POST", admin, "/api/admin/modules/" + m1 + "/exercises", "{\"title\":\"E2\",\"description\":\"d\"}", 201);

        List<String> ids = lessonIds("edit", 0);
        assertEquals(List.of("A", "B", "C"), lessonTitles("edit", 0));
        assertTrue(Files.readString(CONTENT.resolve("edit/module-1-one/lesson-1.md")).contains("id: " + ids.get(0)));

        send("PUT", learner, "/api/progress/lessons/" + ids.get(0), "{\"completed\":true}", 200);

        // reorder lessons: ids and progress stay, only order changes
        send("PUT", admin, "/api/admin/modules/" + m1 + "/lessons/order", ids(List.of(ids.get(2), ids.get(0), ids.get(1))), 204);
        assertEquals(List.of("C", "A", "B"), lessonTitles("edit", 0));
        assertEquals(List.of(ids.get(2), ids.get(0), ids.get(1)), lessonIds("edit", 0));
        send("PUT", admin, "/api/admin/modules/" + m1 + "/lessons/order", ids(List.of(ids.get(0))), 400);

        // renaming and moving files keeps the same lesson
        Path lesson1 = CONTENT.resolve("edit/module-1-one/lesson-1.md");
        Files.move(lesson1, CONTENT.resolve("edit/module-1-one/lesson-renamed.md"));
        Files.move(CONTENT.resolve("edit/module-1-one/lesson-2.md"),
                CONTENT.resolve("edit/module-2-two/lesson-7.md"));
        send("POST", admin, "/api/admin/content/reload", null, 200);
        assertTrue(lessonIds("edit", 0).contains(ids.get(0)));
        assertEquals(List.of(ids.get(1)), lessonIds("edit", 1));
        mvc.perform(get("/api/progress/tools/edit").header("Authorization", learner))
                .andExpect(jsonPath("$.completedLessons").value(1));

        // edit a lesson
        send("PUT", admin, "/api/admin/lessons/" + ids.get(0),
                "{\"title\":\"A renamed\",\"estimatedTime\":7,\"content\":\"new body\"}", 200);
        mvc.perform(get("/api/lessons/" + ids.get(0))).andExpect(jsonPath("$.title").value("A renamed"))
                .andExpect(jsonPath("$.content").value("new body"));
        send("PUT", admin, "/api/admin/lessons/" + java.util.UUID.randomUUID(),
                "{\"title\":\"x\",\"content\":\"y\"}", 404);

        // modules: edit, reorder, delete
        send("PUT", admin, "/api/admin/modules/" + m2, "{\"title\":\"Two renamed\",\"description\":\"d\"}", 200);
        send("PUT", admin, "/api/admin/tools/edit/modules/order", ids(List.of(m2, m1)), 204);
        assertEquals("Two renamed", JsonPath.read(path("edit"), "$.modules[0].title"));
        send("DELETE", admin, "/api/admin/modules/" + m2, null, 204);
        assertEquals(1, (int) JsonPath.read(path("edit"), "$.modules.length()"));

        // exercises: reorder, edit, delete
        List<String> ex = JsonPath.read(path("edit"), "$.modules[0].exercises[*].id");
        send("PUT", admin, "/api/admin/modules/" + m1 + "/exercises/order", ids(List.of(ex.get(1), ex.get(0))), 204);
        assertEquals(List.of("E2", "E1"), JsonPath.read(path("edit"), "$.modules[0].exercises[*].title"));
        send("PUT", admin, "/api/admin/exercises/" + ex.get(0), "{\"title\":\"E1 edited\",\"description\":\"dd\"}", 200);
        send("DELETE", admin, "/api/admin/exercises/" + ex.get(1), null, 204);
        assertEquals(List.of("E1 edited"), JsonPath.read(path("edit"), "$.modules[0].exercises[*].title"));

        // delete a lesson, then the tool
        send("DELETE", admin, "/api/admin/lessons/" + ids.get(0), null, 204);
        mvc.perform(get("/api/lessons/" + ids.get(0))).andExpect(status().isNotFound());
        send("PUT", admin, "/api/admin/tools/edit", "{\"name\":\"Edit 2\",\"description\":\"dd\",\"category\":\"Design\"}", 200);
        mvc.perform(get("/api/tools/edit")).andExpect(jsonPath("$.name").value("Edit 2"))
                .andExpect(jsonPath("$.category").value("Design"));
        send("DELETE", admin, "/api/admin/tools/edit", null, 204);
        mvc.perform(get("/api/tools/edit")).andExpect(status().isNotFound());
        assertFalse(Files.exists(CONTENT.resolve("edit")));
        send("DELETE", token("plain2@example.com"), "/api/admin/tools/edit", null, 403);
    }

    @Test
    void lessonsWithoutIdsGetOneWrittenIntoTheFile() throws Exception {
        String admin = token("editor@example.com");
        Path dir = CONTENT.resolve("legacy/module-1-intro");
        Files.createDirectories(dir);
        Files.writeString(CONTENT.resolve("legacy/metadata.yml"), "name: Legacy\ndescription: d\ncategory: Development\n");
        Files.writeString(dir.resolve("lesson-1.md"), "---\r\ntitle: Old\r\n---\r\nBody\r\n");
        send("POST", admin, "/api/admin/content/reload", null, 200);
        String id = lessonIds("legacy", 0).get(0);
        String text = Files.readString(dir.resolve("lesson-1.md"));
        assertTrue(text.startsWith("---\r\nid: " + id + "\r\ntitle: Old\r\n---\r\n"), text);

        // duplicated files (copy/paste) do not steal the id
        Files.copy(dir.resolve("lesson-1.md"), dir.resolve("lesson-2.md"));
        send("POST", admin, "/api/admin/content/reload", null, 200);
        List<String> ids = lessonIds("legacy", 0);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(id));
        assertEquals(2, ids.stream().distinct().count());
    }
}