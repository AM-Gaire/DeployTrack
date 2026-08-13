package com.deploytrack.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Exercises the full request path: security filters, routing, validation,
// service logic, and real SQL. These catch the class of bug that mocked unit
// tests cannot -- the ownership rules passed unit tests while the API returned
// a misleading error message, because the message came from a layer the unit
// tests never touched.
@AutoConfigureMockMvc
// The simulator would complete deployments on a background thread mid-test,
// changing rows while assertions run.
@TestPropertySource(properties = "deploytrack.simulator.enabled=false")
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
        String name = "attribution-test-" + System.nanoTime();
        long id = createProject(aliceToken, name);

        // Attribution is checked through an admin, since the response to a
        // developer deliberately omits the owner. The record still carries it.
        mockMvc.perform(get("/api/projects/" + id).header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
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
        long id = createProject(aliceToken, "alice-idor-update-" + System.nanoTime());

        // Bob is a valid DEVELOPER, so he clears the role check. Previously
        // the ownership check stopped him with a 403; now visibility scoping
        // stops him earlier and more completely, with a 404 that does not
        // confirm the project exists at all.
        mockMvc.perform(put("/api/projects/" + id)
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"bob-took-it","description":"mine now"}"""))
            .andExpect(status().isNotFound());
    }

    @Test
    void anotherDeveloperCannotDeleteSomeoneElsesProject() throws Exception {
        long id = createProject(aliceToken, "alice-idor-delete-" + System.nanoTime());

        mockMvc.perform(delete("/api/projects/" + id)
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isNotFound());

        // What matters most: the project is still there afterwards.
        mockMvc.perform(get("/api/projects/" + id).header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk());
    }

    // Every other list test passes a search term, so none of them exercised
    // the unfiltered query -- which is the one the application actually uses
    // when you open the projects page. It failed with "function lower(bytea)
    // does not exist", because a null string parameter has no type Postgres
    // can infer.
    @Test
    void listsProjectsWithNoSearchTerm() throws Exception {
        createProject(aliceToken, "unfiltered-" + System.nanoTime());

        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());

        // Also unfiltered, and unscoped, since an admin sees everything.
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void aDeveloperOnlySeesTheirOwnProjectsInTheList() throws Exception {
        String mine = "alice-scoped-" + System.nanoTime();
        createProject(aliceToken, mine);
        String theirs = "bob-scoped-" + System.nanoTime();
        createProject(bobToken, theirs);

        mockMvc.perform(get("/api/projects").param("search", "alice-scoped-")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));

        // Bob's project is invisible to Alice even by name.
        mockMvc.perform(get("/api/projects").param("search", theirs)
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void aDeveloperCannotOpenAnotherDevelopersProjectById() throws Exception {
        long id = createProject(aliceToken, "alice-hidden-" + System.nanoTime());

        // Filtering the list alone would be presentation, not enforcement.
        // 404 rather than 403 so the response does not confirm the project
        // exists.
        mockMvc.perform(get("/api/projects/" + id).header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void aDeveloperCannotReachAnotherDevelopersDeploymentOrLogs() throws Exception {
        long projectId = createProject(aliceToken, "alice-deployments-" + System.nanoTime());
        long deploymentId = triggerDeployment(aliceToken, projectId, "1.0.0", "dev");

        // A deployment inherits its project's visibility, so neither it nor
        // its logs are reachable by id.
        mockMvc.perform(get("/api/deployments/" + deploymentId)
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/deployments/" + deploymentId + "/logs")
                .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void aDeveloperCannotCompleteAnotherDevelopersDeployment() throws Exception {
        long projectId = createProject(aliceToken, "alice-patch-" + System.nanoTime());
        long deploymentId = triggerDeployment(aliceToken, projectId, "1.0.0", "dev");

        // The status callback is a write, so it must be scoped too -- reading
        // is not the only thing an unreachable id could be used for.
        mockMvc.perform(patch("/api/deployments/" + deploymentId + "/status")
                .header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"SUCCESS"}"""))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminSeesEveryProjectAndWhoOwnsIt() throws Exception {
        String name = "alice-owned-" + System.nanoTime();
        createProject(aliceToken, name);

        mockMvc.perform(get("/api/projects").param("search", name)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].createdBy.username").value("alice"));
    }

    @Test
    void aDeveloperIsNotToldWhoOwnsAProject() throws Exception {
        String name = "alice-noowner-" + System.nanoTime();
        long id = createProject(aliceToken, name);

        // Every project a developer can see is their own, so the field would
        // be their own name on every row. Omitted rather than hidden in the
        // UI, since anything sent is readable in the network tab.
        mockMvc.perform(get("/api/projects/" + id).header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.createdBy").doesNotExist());
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

    // The delete tests above and below use projects with no deployments, which
    // is exactly why they kept passing while deleting a real project returned
    // 500. Foreign keys from deployments and logs blocked the delete, and only
    // a project that had actually been used had any.
    @Test
    void deletingAProjectAlsoRemovesItsDeploymentsAndLogs() throws Exception {
        long projectId = createProject(aliceToken, "delete-with-history-" + System.nanoTime());
        long deploymentId = triggerDeployment(aliceToken, projectId, "1.0.0", "staging");

        // Triggering writes a log line, so all three tables have rows.
        mockMvc.perform(get("/api/deployments/" + deploymentId + "/logs")
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(greaterThan(0)));

        mockMvc.perform(delete("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId)
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isNotFound());

        // The children must be gone too, not merely orphaned.
        mockMvc.perform(get("/api/deployments/" + deploymentId)
                .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isNotFound());
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

    private long triggerDeployment(String token, long projectId, String version, String environment)
        throws Exception {
        String response = mockMvc.perform(post("/api/projects/" + projectId + "/deployments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"" + version + "\",\"environment\":\"" + environment + "\"}"))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
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
