package com.deploytrack.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deploytrack.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Exercises the full request path: security filters, routing, validation,
// service logic, and real SQL. These catch the class of bug that mocked unit
// tests cannot -- the ownership rules passed unit tests while the API returned
// a misleading error message, because the message came from a layer the unit
// tests never touched.
@AutoConfigureMockMvc
class ProjectControllerIT extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String aliceToken;
    private String bobToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        aliceToken = registerAndLogin("alice", "alice@test.local");
        bobToken = registerAndLogin("bob", "bob@test.local");
        adminToken = login("admin@test.local", "test-admin-password");
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/projects"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsGarbageToken() throws Exception {
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer not-a-real-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsEmptyPageRatherThanErrorWhenNoResults() throws Exception {
        // The frontend must be able to tell "loaded, nothing here" apart from
        // "failed to load" -- an empty list is a 200, never a 404.
        mockMvc.perform(get("/api/projects").param("search", "nothing-matches-this")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void createsProjectAttributedToTheAuthenticatedCaller() throws Exception {
        mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"attribution-test","description":"owned by alice"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdBy.username").value("alice"));
    }

    @Test
    void rejectsBlankNameWithFieldLevelError() throws Exception {
        mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"","description":"no name"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void rejectsDuplicateProjectName() throws Exception {
        createProject(aliceToken, "duplicate-check");

        mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"duplicate-check","description":"again"}"""))
            .andExpect(status().isConflict());
    }

    @Test
    void ownerCanUpdateTheirOwnProject() throws Exception {
        long id = createProject(aliceToken, "alice-owned-update");

        mockMvc.perform(put("/api/projects/" + id)
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"alice-owned-update","description":"edited by owner"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("edited by owner"));
    }

    @Test
    void anotherDeveloperCannotUpdateSomeoneElsesProject() throws Exception {
        long id = createProject(aliceToken, "alice-idor-update");

        // Bob is a valid DEVELOPER, so he clears the role check. Only the
        // ownership check stops him -- this is the IDOR case.
        mockMvc.perform(put("/api/projects/" + id)
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"bob-took-it","description":"mine now"}"""))
            .andExpect(status().isForbidden())
            // The message must name the real reason. Saying "your role does
            // not permit this" would be false: Bob's role does permit editing.
            .andExpect(jsonPath("$.message").value(containsString("projects you created")));
    }

    @Test
    void anotherDeveloperCannotDeleteSomeoneElsesProject() throws Exception {
        long id = createProject(aliceToken, "alice-idor-delete");

        mockMvc.perform(delete("/api/projects/" + id)
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isForbidden());

        // The project must still be there afterwards.
        mockMvc.perform(get("/api/projects/" + id).header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk());
    }

    @Test
    void anyAuthenticatedUserCanReadAnyProject() throws Exception {
        long id = createProject(aliceToken, "alice-readable");

        mockMvc.perform(get("/api/projects/" + id).header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanUpdateAnyProject() throws Exception {
        long id = createProject(aliceToken, "admin-override-update");

        mockMvc.perform(put("/api/projects/" + id)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"admin-override-update","description":"admin edited"}"""))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanDeleteAnyProject() throws Exception {
        long id = createProject(aliceToken, "admin-override-delete");

        mockMvc.perform(delete("/api/projects/" + id).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + id).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForMissingProject() throws Exception {
        mockMvc.perform(get("/api/projects/999999").header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    private long createProject(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"description\":\"test fixture\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String registerAndLogin(String username, String email) throws Exception {
        // Registration is idempotent across tests only in the sense that a
        // repeat returns 409; each test method gets a fresh unique project
        // name instead of a fresh user, so tolerate the conflict here.
        mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                + "\",\"password\":\"test-password-123\"}"));
        return login(email, "test-password-123");
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
