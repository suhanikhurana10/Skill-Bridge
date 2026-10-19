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

/**
 * End-to-end HTTP tests on the real Spring application (real JSON mapping, validation, routing, security filter chain).
 * Profile "test" = in-memory H2 database in MySQL mode (see src/test/resources/application-test.yml).
 * The seeded demo accounts are used where a ready-made worker (id 1) / employer (owns job 1) is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTests {

    static final String DEMO_PASSWORD = "Demo@1234";

    @Autowired
    MockMvc mvc;

    final ObjectMapper json = new ObjectMapper();

    /** Logs in and returns the JWT. */
    String login(String phone, String password) throws Exception {
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("access_token").asText();
    }

    /** Creates a new account and returns its JWT. */
    String register(String phone, String role) throws Exception {
        String res = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"Passw0rd!x\",\"name\":\"Test " + role
                                + "\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("access_token").asText();
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void healthAndDashboard() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.services.ivr_ussd_telecom").value("UP"));
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KaayakaSetu")));
        mvc.perform(get("/static/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/static/css/style.css")).andExpect(status().isOk());
    }

    @Test
    void workersListWorksWithAndWithoutTrailingSlashAndUsesSnakeCase() throws Exception {
        mvc.perform(get("/api/v1/workers/?limit=30")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].daily_rate").exists())
                .andExpect(jsonPath("$[0].created_at").exists());
        mvc.perform(get("/api/v1/workers")).andExpect(status().isOk());
    }

    @Test
    void workerCreatesProfileOncePerAccount() throws Exception {
        String token = register("9845999001", "worker");
        String body = """
                {"name":"Sharanu Gowda","phone":"9845999001","trade":"Plumber","experience_years":4,
                 "daily_rate":750.0,"city":"Bengaluru","area":"Malleshwaram","language_pref":"kannada","source":"web"}""";
        mvc.perform(post("/api/v1/workers/").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sharanu Gowda"))
                .andExpect(jsonPath("$.hourly_rate").value(93.8));
        // the account is now linked to the profile
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("worker"))
                .andExpect(jsonPath("$.worker_id").isNumber());
        // second profile for the same account is refused
        mvc.perform(post("/api/v1/workers/").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void profilePhoneMustMatchAccountPhone() throws Exception {
        String token = register("9845999002", "worker");
        mvc.perform(post("/api/v1/workers/").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"phone\":\"9000000000\",\"trade\":\"Plumber\",\"daily_rate\":700,\"city\":\"Mysuru\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationErrorsAre422WithDetail() throws Exception {
        String token = register("9000000099", "worker");
        mvc.perform(post("/api/v1/workers/").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Rate\",\"phone\":\"9000000099\",\"trade\":\"Plumber\",\"city\":\"Mysuru\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").exists());
        mvc.perform(get("/api/v1/workers/?limit=0")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/workers/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Worker profile not found"));
    }

    @Test
    void jobPostingAndMatching() throws Exception {
        String employer = login("9900112233", DEMO_PASSWORD);
        String worker = login("9845011223", DEMO_PASSWORD);

        mvc.perform(post("/api/v1/jobs/").header("Authorization", bearer(employer)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"title":"Apartment Kitchen Pipe Leak Fix","trade":"Plumber","employer_name":"Narayana Swamy",
                         "employer_phone":"9900887766","city":"Bengaluru","area":"Rajajinagar","duration_days":1,
                         "wage_offered":850.0,"urgency":"urgent","workers_needed":1}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wage_offered").value(850.0));

        mvc.perform(get("/api/v1/match/worker/1/jobs?max_radius_km=100").header("Authorization", bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].match_score").exists())
                .andExpect(jsonPath("$[0].breakdown.skill_score").exists())
                .andExpect(jsonPath("$[0].job.employer_name").exists());
        mvc.perform(get("/api/v1/match/worker/9999/jobs").header("Authorization", bearer(worker)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/match/job/1/workers").header("Authorization", bearer(employer)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/jobs/mine").header("Authorization", bearer(employer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    void matchingIsRestrictedToTheOwner() throws Exception {
        String otherWorker = register("9845999003", "worker");
        String otherEmployer = register("9900999003", "employer");
        mvc.perform(get("/api/v1/match/worker/1/jobs").header("Authorization", bearer(otherWorker)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/match/job/1/workers").header("Authorization", bearer(otherEmployer)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/match/worker/1/jobs")).andExpect(status().isUnauthorized());
    }

    @Test
    void contactFlowSmsAndAccessRules() throws Exception {
        String worker = login("9845011223", DEMO_PASSWORD);        // Ramesh Gowda
        String employer = login("9900112233", DEMO_PASSWORD);      // owns job 1
        String stranger = register("9845999004", "worker");

        // the worker applies for job 1 - the sender comes from the token, not from the request body
        String res = mvc.perform(post("/api/v1/contact/").header("Authorization", bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiver_type\":\"employer\",\"job_id\":1,\"message\":\"Wiring work\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.sender_name").value("Ramesh Gowda"))
                .andExpect(jsonPath("$.receiver_name").value("Shree Manjunatha Traders"))
                .andReturn().getResponse().getContentAsString();
        int id = json.readTree(res).get("id").asInt();

        // SMS goes through the gateway (mock by default)
        mvc.perform(post("/api/v1/contact/" + id + "/dispatch-sms").header("Authorization", bearer(worker)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatch_status").value("SENT"))
                .andExpect(jsonPath("$.provider").value("MockSmsGateway"));
        // only the sender may trigger the SMS
        mvc.perform(post("/api/v1/contact/" + id + "/dispatch-sms").header("Authorization", bearer(employer)))
                .andExpect(status().isForbidden());

        // strangers cannot read it, the employer can
        mvc.perform(get("/api/v1/contact/" + id).header("Authorization", bearer(stranger))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/contact/" + id).header("Authorization", bearer(employer))).andExpect(status().isOk());

        // only the receiver may accept
        mvc.perform(patch("/api/v1/contact/" + id).header("Authorization", bearer(worker))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"accepted\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/contact/" + id).header("Authorization", bearer(employer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        mvc.perform(get("/api/v1/contact/me").header("Authorization", bearer(employer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
        mvc.perform(get("/api/v1/contact/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void employerCanContactWorkerAboutOwnJob() throws Exception {
        String employer = login("9900112233", DEMO_PASSWORD);
        mvc.perform(post("/api/v1/contact/").header("Authorization", bearer(employer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiver_type\":\"worker\",\"receiver_id\":1,\"job_id\":1,\"message\":\"Can you come?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sender_name").value("Shree Manjunatha Traders"))
                .andExpect(jsonPath("$.receiver_name").value("Ramesh Gowda"));
        // job 2 belongs to nobody (demo data) -> not the caller's job
        mvc.perform(post("/api/v1/contact/").header("Authorization", bearer(employer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiver_type\":\"worker\",\"receiver_id\":1,\"job_id\":2}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wageFeedsLocationAndInsights() throws Exception {
        mvc.perform(post("/api/v1/wage/predict").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade\":\"Electrician\",\"city\":\"Bengaluru\",\"experience_years\":5,\"has_own_tools\":true,\"is_urgent\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city_tier").value("Tier-1"))
                .andExpect(jsonPath("$.recommended_daily_wage").value(1450.0))
                .andExpect(jsonPath("$.regional_explanations.kannada").exists());
        mvc.perform(get("/api/v1/feeds/jobs?city=Bengaluru")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].distance_km").exists());
        mvc.perform(get("/api/v1/feeds/workers?city=Bengaluru")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/feeds/insights")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total_workers").exists())
                .andExpect(jsonPath("$.top_trades[0].avg_rate").exists());
        mvc.perform(get("/api/v1/location/cities")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].primary_lang").exists());
        mvc.perform(get("/api/v1/location/distance?lat1=12.9716&lon1=77.5946&lat2=12.2958&lon2=76.6394"))
                .andExpect(jsonPath("$.distance_km").value(128.02));
    }

    @Test
    void ussdAndIvrEndpoints() throws Exception {
        // JSON simulator endpoint: message keeps the "CON " prefix, the frontend strips it
        mvc.perform(post("/api/v1/telecom/ussd").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"session_id\":\"it_1\",\"phone_number\":\"9845111222\",\"user_input\":\"*345#\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CON"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("CON KaayakaSetu")));

        // provider webhook: form-encoded in, plain text out
        mvc.perform(post("/api/v1/telecom/ussd/form").param("sessionId", "it_2")
                        .param("phoneNumber", "9845111333").param("text", ""))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("CON ")));

        mvc.perform(post("/api/v1/telecom/ivr").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"call_sid\":\"C1\",\"caller_phone\":\"9845111444\",\"digits\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("gather"))
                .andExpect(jsonPath("$.twiml_xml").exists());

        mvc.perform(post("/api/v1/telecom/ivr/twiml").param("CallSid", "C2").param("From", "9845111555"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<Response>")));

        mvc.perform(post("/api/v1/telecom/ussd/reset").param("session_id", "it_1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("success"));
    }
}
