package com.kaayakasetu;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Register / login / JWT checks and the role + ownership rules. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTests {

    @Autowired
    MockMvc mvc;

    final ObjectMapper json = new ObjectMapper();

    private String registerBody(String phone, String password, String role) {
        return "{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\",\"name\":\"Some One\",\"role\":\"" + role + "\"}";
    }

    private String register(String phone, String role) throws Exception {
        String res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(phone, "Passw0rd!x", role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("access_token").asText();
    }

    private static String bearer(String t) {
        return "Bearer " + t;
    }

    private static final String JOB = """
            {"title":"Tap repair","trade":"Plumber","employer_name":"Some One","employer_phone":"9900000000",
             "city":"Mysuru","wage_offered":700.0}""";

    // ------------------------------------------------------------------ register / login

    @Test
    void registerReturnsTokenAndNeverTheHash() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("9700000001", "Passw0rd!x", "worker")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber())
                .andExpect(jsonPath("$.user.role").value("worker"))
                .andExpect(jsonPath("$.user.password_hash").doesNotExist());
    }

    @Test
    void duplicatePhoneAndBadInputAreRejected() throws Exception {
        register("9700000002", "employer");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("9700000002", "Passw0rd!x", "employer")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("9700000003", "short", "worker")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("12345", "Passw0rd!x", "worker")))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("9700000004", "Passw0rd!x", "admin")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginChecksThePassword() throws Exception {
        register("9700000005", "worker");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9700000005\",\"password\":\"Passw0rd!x\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9700000005\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid phone number or password"));
        // unknown phone gives the very same answer
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"9700009999\",\"password\":\"Passw0rd!x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid phone number or password"));
    }

    // ------------------------------------------------------------------ tokens

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").exists());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());

        String token = register("9700000006", "worker");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("9700000006"));

        // flip the last character of the signature: the token must be rejected
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        mvc.perform(get("/api/v1/jobs/")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/workers/")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/feeds/insights")).andExpect(status().isOk());
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/openapi.json")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ roles

    @Test
    void postingJobsNeedsAnEmployerAccount() throws Exception {
        mvc.perform(post("/api/v1/jobs/").contentType(MediaType.APPLICATION_JSON).content(JOB))
                .andExpect(status().isUnauthorized());

        String worker = register("9700000007", "worker");
        mvc.perform(post("/api/v1/jobs/").header("Authorization", bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON).content(JOB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").exists());

        String employer = register("9700000008", "employer");
        mvc.perform(post("/api/v1/jobs/").header("Authorization", bearer(employer))
                        .contentType(MediaType.APPLICATION_JSON).content(JOB))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ ownership

    @Test
    void employerEditsOnlyOwnJobs() throws Exception {
        String owner = register("9700000009", "employer");
        String other = register("9700000010", "employer");
        String created = mvc.perform(post("/api/v1/jobs/").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(JOB))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        String patch = "{\"status\":\"filled\"}";

        mvc.perform(patch("/api/v1/jobs/" + id).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/jobs/" + id).contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/jobs/" + id).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("filled"));
        mvc.perform(patch("/api/v1/jobs/99999").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isNotFound());
    }

    @Test
    void workerEditsOnlyOwnProfile() throws Exception {
        String token = register("9700000011", "worker");
        String created = mvc.perform(post("/api/v1/workers/").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Own Profile\",\"phone\":\"9700000011\",\"trade\":\"Painter\",\"daily_rate\":650,\"city\":\"Mysuru\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long ownId = json.readTree(created).get("id").asLong();

        mvc.perform(patch("/api/v1/workers/" + ownId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"daily_rate\":720}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily_rate").value(720.0));
        // worker 1 is somebody else's profile
        mvc.perform(patch("/api/v1/workers/1").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"daily_rate\":1}"))
                .andExpect(status().isForbidden());
        // an employer account cannot edit worker profiles at all
        String employer = register("9700000012", "employer");
        mvc.perform(patch("/api/v1/workers/" + ownId).header("Authorization", bearer(employer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"daily_rate\":1}"))
                .andExpect(status().isForbidden());
    }
}
