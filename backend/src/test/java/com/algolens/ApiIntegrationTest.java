package com.algolens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end through the real HTTP layer: Flyway migrations, JPA mappings, security rules,
 * validation and the execution pipeline all have to be right for these to pass.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    private static final String BUBBLE_SORT = """
            int[] arr = {5, 2, 8, 1};

            for (int i = 0; i < arr.length - 1; i++) {
                for (int j = 0; j < arr.length - i - 1; j++) {
                    if (arr[j] > arr[j + 1]) {
                        int temp = arr[j];
                        arr[j] = arr[j + 1];
                        arr[j + 1] = temp;
                    }
                }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------ public surface

    @Test
    @DisplayName("the meta endpoint advertises Java as supported without authentication")
    void metaIsPublic() throws Exception {
        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousExecutionAllowed").value(true))
                .andExpect(jsonPath("$.languages[?(@.id=='JAVA')].supported").value(true))
                .andExpect(jsonPath("$.creditCosts.CODE_EXECUTION").value(1));
    }

    @Test
    @DisplayName("the seeded problem library is readable without an account")
    void problemsArePublic() throws Exception {
        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='bubble-sort')].title").value("Bubble Sort"));

        mockMvc.perform(get("/api/problems/bubble-sort"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.starterCode").isNotEmpty());
    }

    @Test
    @DisplayName("anonymous callers can run code but get no history row")
    void anonymousExecution() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/executions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(BUBBLE_SORT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace.status").value("SUCCESS"))
                .andExpect(jsonPath("$.executionId").doesNotExist())
                .andExpect(jsonPath("$.creditsSpent").value(0))
                .andReturn();

        JsonNode trace = json(result).get("trace");
        assertThat(trace.get("steps")).isNotEmpty();
        assertThat(trace.get("metrics").get("swaps").asInt()).isPositive();
    }

    @Test
    @DisplayName("protected endpoints answer 401 with the standard error shape")
    void protectedEndpointsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    // ------------------------------------------------------------------ validation

    @Test
    @DisplayName("validation failures come back as per-field messages")
    void validationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "not-an-email", "password": "short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("registering the same email twice is a conflict")
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @DisplayName("a wrong password never reveals whether the account exists")
    void badCredentialsAreOpaque() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "definitely-wrong"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nobody-%s@example.com", "password": "whatever12"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"));
    }

    // ------------------------------------------------------------------ the signed-in journey

    @Test
    @DisplayName("register, run, replay from history, save a snippet, read the dashboard")
    void fullJourney() throws Exception {
        String email = uniqueEmail();
        String token = register(email);

        // Signup grant is on the account.
        mockMvc.perform(get("/api/credits").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100))
                .andExpect(jsonPath("$.enforced").value(false));

        // Run code: recorded, metered, and replayable.
        MvcResult run = mockMvc.perform(post("/api/executions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody(BUBBLE_SORT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").exists())
                .andExpect(jsonPath("$.creditsSpent").value(1))
                .andExpect(jsonPath("$.creditBalance").value(99))
                .andReturn();

        long executionId = json(run).get("executionId").asLong();
        int stepCount = json(run).get("trace").get("steps").size();

        // The stored trace replays without re-running anything.
        MvcResult replay = mockMvc.perform(get("/api/executions/" + executionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn();
        assertThat(json(replay).get("trace").get("steps").size()).isEqualTo(stepCount);

        // History lists it.
        mockMvc.perform(get("/api/executions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].replayable").value(true));

        // The ledger explains where the credit went.
        mockMvc.perform(get("/api/credits/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("CODE_EXECUTION"))
                .andExpect(jsonPath("$.content[0].amount").value(-1))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(99))
                .andExpect(jsonPath("$.content[1].type").value("SIGNUP_GRANT"));

        // Save a snippet and read it back.
        MvcResult saved = mockMvc.perform(post("/api/saved-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "My Bubble Sort", "language": "JAVA",
                                 "code": "int[] a = {2, 1};"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long savedId = json(saved).get("id").asLong();

        mockMvc.perform(get("/api/saved-code/" + savedId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("int[] a = {2, 1};"));

        // Dashboard aggregates it all.
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExecutions").value(1))
                .andExpect(jsonPath("$.successfulExecutions").value(1))
                .andExpect(jsonPath("$.savedSnippets").value(1))
                .andExpect(jsonPath("$.creditBalance").value(99))
                .andExpect(jsonPath("$.recentExecutions[0].id").value(executionId));

        mockMvc.perform(delete("/api/saved-code/" + savedId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a syntax error is a 200 with a COMPILE_ERROR trace and costs nothing")
    void compileErrorsAreFree() throws Exception {
        String token = register(uniqueEmail());

        mockMvc.perform(post("/api/executions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("int x = ;")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace.status").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.trace.errorLine").value(1))
                .andExpect(jsonPath("$.creditsSpent").value(0))
                .andExpect(jsonPath("$.creditBalance").value(100));
    }

    @Test
    @DisplayName("one user cannot read another user's execution")
    void historyIsScopedToItsOwner() throws Exception {
        String ownerToken = register(uniqueEmail());
        MvcResult run = mockMvc.perform(post("/api/executions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody("int x = 1;")))
                .andReturn();
        long executionId = json(run).get("executionId").asLong();

        String intruderToken = register(uniqueEmail());
        mockMvc.perform(get("/api/executions/" + executionId)
                        .header("Authorization", "Bearer " + intruderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a forged token is rejected")
    void forgedTokensAreRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the AI endpoint answers with the local explainer when no key is configured")
    void aiFallsBackToTheLocalExplainer() throws Exception {
        String token = register(uniqueEmail());

        mockMvc.perform(post("/api/ai/explain")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode": "COMPLEXITY", "language": "JAVA",
                                 "code": "int[] a = {2, 1};",
                                 "traceExcerpt": ["step 1 | line 1 | DECLARE | int[] a"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("heuristic"))
                .andExpect(jsonPath("$.creditsSpent").value(0))
                .andExpect(jsonPath("$.explanation").isNotEmpty());
    }

    // ------------------------------------------------------------------ helpers

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        return json(result).get("token").asText();
    }

    private static String registerBody(String email) {
        return """
                {"name": "Test User", "email": "%s", "password": "correct-horse"}
                """.formatted(email);
    }

    private String runBody(String code) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("language", "JAVA", "code", code));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
