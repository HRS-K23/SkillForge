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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
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
        r.add("skillforge.content.root", CONTENT::toString);
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:editing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    @Autowired WebApplicationContext ctx;
    MockMvc mvc;

    @BeforeEach
    void setUp() throws IOException {
        resetContentRoot();
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private void resetContentRoot() throws IOException {
        if (!Files.exists(CONTENT)) {
            Files.createDirectories(CONTENT);
            return;
        }
        try (var paths = Files.walk(CONTENT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(CONTENT)) {
                    Files.deleteIfExists(path);
                }
            }
        }
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

    private Fixture fixture() throws Exception {
        String admin = token("editor@example.com");
        send("POST", admin, "/api/admin/tools", "{\"slug\":\"edit\",\"name\":\"Edit\",\"description\":\"d\","
                + "\"category\":\"Development\"}", 201);
        send("POST", admin, "/api/admin/tools/edit/modules", "{\"title\":\"One\"}", 201);
        send("POST", admin, "/api/admin/tools/edit/modules", "{\"title\":\"Two\"}", 201);
        String moduleOneId = JsonPath.read(path("edit"), "$.modules[0].id");
        String moduleTwoId = JsonPath.read(path("edit"), "$.modules[1].id");
        for (String title : List.of("A", "B", "C")) {
            send("POST", admin, "/api/admin/modules/" + moduleOneId + "/lessons",
                    "{\"title\":\"" + title + "\",\"content\":\"x\"}", 201);
        }
        send("POST", admin, "/api/admin/modules/" + moduleOneId + "/exercises",
                "{\"title\":\"E1\",\"description\":\"d\"}", 201);
        send("POST", admin, "/api/admin/modules/" + moduleOneId + "/exercises",
                "{\"title\":\"E2\",\"description\":\"d\"}", 201);

        List<String> lessonIds = lessonIds("edit", 0);
        assertEquals(List.of("A", "B", "C"), lessonTitles("edit", 0));
        assertTrue(Files.readString(CONTENT.resolve("edit/module-1-one/lesson-1.md")).contains("id: " + lessonIds.get(0)));
        return new Fixture(admin, moduleOneId, moduleTwoId, lessonIds);
    }

    private record Fixture(String admin, String moduleOneId, String moduleTwoId, List<String> lessonIds) {}

    @Test
    void adminReordersContentWithoutLosingProgress() throws Exception {
        Fixture f = fixture();
        String learner = token("learner@example.com");
        send("PUT", learner, "/api/progress/lessons/" + f.lessonIds().get(0), "{\"completed\":true}", 200);

        send("PUT", f.admin(), "/api/admin/modules/" + f.moduleOneId() + "/lessons/order",
                ids(List.of(f.lessonIds().get(2), f.lessonIds().get(0), f.lessonIds().get(1))), 204);
        assertEquals(List.of("C", "A", "B"), lessonTitles("edit", 0));
        assertEquals(List.of(f.lessonIds().get(2), f.lessonIds().get(0), f.lessonIds().get(1)), lessonIds("edit", 0));
        send("PUT", f.admin(), "/api/admin/modules/" + f.moduleOneId() + "/lessons/order",
                ids(List.of(f.lessonIds().get(0))), 400);

        Path lesson1 = CONTENT.resolve("edit/module-1-one/lesson-1.md");
        Files.move(lesson1, CONTENT.resolve("edit/module-1-one/lesson-renamed.md"));
        Files.move(CONTENT.resolve("edit/module-1-one/lesson-2.md"),
                CONTENT.resolve("edit/module-2-two/lesson-7.md"));
        send("POST", f.admin(), "/api/admin/content/reload", null, 200);
        assertTrue(lessonIds("edit", 0).contains(f.lessonIds().get(0)));
        assertEquals(List.of(f.lessonIds().get(1)), lessonIds("edit", 1));
        mvc.perform(get("/api/progress/tools/edit").header("Authorization", learner))
                .andExpect(jsonPath("$.completedLessons").value(1));

        List<String> ex = JsonPath.read(path("edit"), "$.modules[0].exercises[*].id");
        send("PUT", f.admin(), "/api/admin/modules/" + f.moduleOneId() + "/exercises/order",
                ids(List.of(ex.get(1), ex.get(0))), 204);
        assertEquals(List.of("E2", "E1"), JsonPath.read(path("edit"), "$.modules[0].exercises[*].title"));

        send("PUT", f.admin(), "/api/admin/tools/edit/modules/order", ids(List.of(f.moduleTwoId(), f.moduleOneId())), 204);
        assertEquals("Two", JsonPath.read(path("edit"), "$.modules[0].title"));
    }

    @Test
    void adminEditsContent() throws Exception {
        Fixture f = fixture();
        send("PUT", f.admin(), "/api/admin/lessons/" + f.lessonIds().get(0),
                "{\"title\":\"A renamed\",\"estimatedTime\":7,\"content\":\"new body\"}", 200);
        mvc.perform(get("/api/lessons/" + f.lessonIds().get(0))).andExpect(jsonPath("$.title").value("A renamed"))
                .andExpect(jsonPath("$.content").value("new body"));
        send("PUT", f.admin(), "/api/admin/lessons/" + UUID.randomUUID(), "{\"title\":\"x\",\"content\":\"y\"}", 404);

        send("PUT", f.admin(), "/api/admin/modules/" + f.moduleTwoId(), "{\"title\":\"Two renamed\",\"description\":\"d\"}", 200);
        assertEquals("Two renamed", JsonPath.read(path("edit"), "$.modules[1].title"));

        List<String> ex = JsonPath.read(path("edit"), "$.modules[0].exercises[*].id");
        send("PUT", f.admin(), "/api/admin/exercises/" + ex.get(0), "{\"title\":\"E1 edited\",\"description\":\"dd\"}", 200);
        assertEquals(List.of("E1 edited", "E2"), JsonPath.read(path("edit"), "$.modules[0].exercises[*].title"));

        send("PUT", f.admin(), "/api/admin/tools/edit", "{\"name\":\"Edit 2\",\"description\":\"dd\",\"category\":\"Design\"}", 200);
        mvc.perform(get("/api/tools/edit")).andExpect(jsonPath("$.name").value("Edit 2"))
                .andExpect(jsonPath("$.category").value("Design"));
    }

    @Test
    void adminDeletesContentAndRejectsNonAdmins() throws Exception {
        Fixture f = fixture();
        List<String> ex = JsonPath.read(path("edit"), "$.modules[0].exercises[*].id");
        send("DELETE", f.admin(), "/api/admin/exercises/" + ex.get(1), null, 204);
        assertEquals(List.of("E1"), JsonPath.read(path("edit"), "$.modules[0].exercises[*].title"));

        send("DELETE", f.admin(), "/api/admin/lessons/" + f.lessonIds().get(0), null, 204);
        mvc.perform(get("/api/lessons/" + f.lessonIds().get(0))).andExpect(status().isNotFound());

        send("DELETE", f.admin(), "/api/admin/modules/" + f.moduleTwoId(), null, 204);
        assertEquals(1, (int) JsonPath.read(path("edit"), "$.modules.length()"));

        send("DELETE", f.admin(), "/api/admin/tools/edit", null, 204);
        mvc.perform(get("/api/tools/edit")).andExpect(status().isNotFound());
        assertFalse(Files.exists(CONTENT.resolve("edit")));
        send("DELETE", token("plain2@example.com"), "/api/admin/tools/edit", null, 403);
    }

    @Test
    void moduleFolderMissingAndInvalidOrderAreRejected() throws Exception {
        Fixture f = fixture();
        Files.move(CONTENT.resolve("edit/module-2-two"), CONTENT.resolve("edit/module-2-two-missing"));
        send("PUT", f.admin(), "/api/admin/modules/" + f.moduleTwoId(),
                "{\"title\":\"Two renamed\",\"description\":\"d\"}", 409);
        send("PUT", f.admin(), "/api/admin/modules/" + f.moduleOneId() + "/lessons/order",
                ids(List.of(f.lessonIds().get(0), f.lessonIds().get(0), f.lessonIds().get(1))), 400);
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
        String normalized = text.replace("\r\n", "\n");
        assertTrue(normalized.startsWith("---\nid: " + id + "\ntitle: Old\n---\n"), text);

        Files.copy(dir.resolve("lesson-1.md"), dir.resolve("lesson-2.md"));
        send("POST", admin, "/api/admin/content/reload", null, 200);
        List<String> ids = lessonIds("legacy", 0);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(id));
        assertEquals(2, ids.stream().distinct().count());
    }
}
