package com.project.skillforge.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.project.skillforge.users.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(AccountTests.Capture.class)
class AccountTests {

    @TestConfiguration
    static class Capture {
        static final List<String> LINKS = new ArrayList<>();

        @Bean
        @Primary
        ResetLinkSender capturingSender() {
            return (User user, String link) -> LINKS.add(user.getEmail() + " " + link);
        }
    }

    @Autowired WebApplicationContext ctx;
    @Autowired JwtEncoder encoder;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        Capture.LINKS.clear();
    }

    private String register(String email) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ann\",\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.accessToken");
    }

    private void login(String email, String password, int expected) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expected));
    }

    private void me(String token, int expected) throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().is(expected));
    }

    @Test
    void expiredTokensGetADistinctCode() throws Exception {
        String token = register("expired@example.com");
        String sub = JsonPath.read(new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1])), "$.sub");
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(sub).issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600)).claim("role", "LEARNER").claim("tv", 0).build();
        String old = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + old))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void changingPasswordRevokesOldSessions() throws Exception {
        String old = register("change@example.com");
        mvc.perform(put("/api/users/me/password").header("Authorization", "Bearer " + old)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        me(old, 200);

        String res = mvc.perform(put("/api/users/me/password").header("Authorization", "Bearer " + old)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        me(old, 401);
        me(JsonPath.read(res, "$.accessToken"), 200);
        login("change@example.com", "password123", 401);
        login("change@example.com", "newpassword1", 200);
    }

    @Test
    void forgotAndResetPassword() throws Exception {
        String old = register("forgot@example.com");

        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\"}")).andExpect(status().isAccepted());
        assertTrue(Capture.LINKS.isEmpty());

        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Forgot@Example.com\"}")).andExpect(status().isAccepted());
        assertEquals(1, Capture.LINKS.size());
        String token = Capture.LINKS.get(0).substring(Capture.LINKS.get(0).indexOf("token=") + 6);

        // a second request inside the throttle window does not send another link
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"forgot@example.com\"}")).andExpect(status().isAccepted());
        assertEquals(1, Capture.LINKS.size());

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-token\",\"newPassword\":\"brandnew123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"brandnew123\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"another1234\"}"))
                .andExpect(status().isBadRequest());

        me(old, 401);
        login("forgot@example.com", "password123", 401);
        login("forgot@example.com", "brandnew123", 200);
    }

    @Test
    void usersCanSoftDeleteTheirAccount() throws Exception {
        String token = register("leaving@example.com");
        mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"nope-nope\"}"))
                .andExpect(status().isBadRequest());
        me(token, 200);

        mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"password123\"}"))
                .andExpect(status().isNoContent());

        me(token, 401);
        login("leaving@example.com", "password123", 401);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"leaving@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"leaving@example.com\"}")).andExpect(status().isAccepted());
        assertTrue(Capture.LINKS.isEmpty());
    }
}