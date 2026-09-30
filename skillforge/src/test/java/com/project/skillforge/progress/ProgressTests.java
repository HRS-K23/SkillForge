package com.project.skillforge.progress;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.skillforge.learning.Lesson;
import com.project.skillforge.learning.LessonRepository;
import java.util.UUID;
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

@SpringBootTest(properties = "skillforge.content.root=src/test/resources/content")
class ProgressTests {

    @Autowired WebApplicationContext ctx;
    @Autowired LessonRepository lessons;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private String token(String email) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"P\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(res);
        m.find();
        return "Bearer " + m.group(1);
    }

    private UUID lessonId(String title) {
        return lessons.findAll().stream().filter(l -> l.getTitle().equals(title)).findFirst()
                .map(Lesson::getId).orElseThrow();
    }

    private void set(String token, UUID id, boolean completed) throws Exception {
        mvc.perform(put("/api/progress/lessons/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":" + completed + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(completed));
    }

    @Test
    void completionDrivesModuleAndPathPercentages() throws Exception {
        String token = token("prog1@example.com");
        // alpha has 3 valid lessons: 2 in module 1, 1 in module 2
        set(token, lessonId("What is Alpha?"), true);
        set(token, lessonId("What is Alpha?"), true); // idempotent

        mvc.perform(get("/api/progress/tools/alpha").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLessons").value(3))
                .andExpect(jsonPath("$.completedLessons").value(1))
                .andExpect(jsonPath("$.percent").value(33))
                .andExpect(jsonPath("$.modules[0].percent").value(50))
                .andExpect(jsonPath("$.modules[1].percent").value(0));

        mvc.perform(get("/api/progress").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].toolSlug").value("alpha"));

        set(token, lessonId("What is Alpha?"), false);
        mvc.perform(get("/api/progress").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void progressIsPerUser() throws Exception {
        String a = token("prog2@example.com");
        String b = token("prog3@example.com");
        set(a, lessonId("What is Alpha?"), true);
        mvc.perform(get("/api/progress/tools/alpha").header("Authorization", b))
                .andExpect(jsonPath("$.completedLessons").value(0));
    }

    @Test
    void requiresAuthAndValidLesson() throws Exception {
        mvc.perform(get("/api/progress")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/progress/lessons/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/progress/lessons/" + UUID.randomUUID()).header("Authorization", token("prog4@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LESSON_NOT_FOUND"));
        mvc.perform(get("/api/progress/tools/nope").header("Authorization", token("prog5@example.com")))
                .andExpect(status().isNotFound());
    }
}
