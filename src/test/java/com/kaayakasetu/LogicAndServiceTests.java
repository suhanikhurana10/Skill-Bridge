package com.kaayakasetu;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaayakasetu.common.DatabaseInitializer;
import com.kaayakasetu.common.Fmt;
import com.kaayakasetu.common.PyMath;
import com.kaayakasetu.contact.*;
import com.kaayakasetu.job.*;
import com.kaayakasetu.location.LocationService;
import com.kaayakasetu.location.ResolvedCity;
import com.kaayakasetu.matching.*;
import com.kaayakasetu.telecom.*;
import com.kaayakasetu.wage.*;
import com.kaayakasetu.worker.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.kaayakasetu.auth.UserRepository;
import com.kaayakasetu.sms.MockSmsGateway;
import com.kaayakasetu.sms.SmsGateway;
import com.kaayakasetu.sms.SmsSendResult;

/**
 * Plain unit tests (no Spring context, so they run in about a second). They build the services by hand on a
 * throw-away in-memory H2 database (MySQL mode). The expected numbers were produced by running the ORIGINAL Python code,
 * so these tests prove the Java port gives the same answers.
 */
class LogicAndServiceTests {

    JdbcTemplate jdbc;
    LocationService location;
    WorkerService workers;
    JobService jobs;
    FairWageService wage;
    MatchingService matching;
    ContactService contacts;
    TelecomService telecom;

    @BeforeEach
    void setUp() {
        // a fresh, uniquely named in-memory database for every test
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        this.jdbc = jdbc;
        new DatabaseInitializer(jdbc, new BCryptPasswordEncoder(4), false).afterPropertiesSet();

        ObjectMapper om = new ObjectMapper();
        location = new LocationService();
        workers = new WorkerService(new WorkerRepository(jdbc), location);
        jobs = new JobService(new JobRepository(jdbc), location);
        wage = new FairWageService(location);
        matching = new MatchingService(location, workers, jobs);
        contacts = new ContactService(new ContactRepository(jdbc), workers, jobs, new UserRepository(jdbc),
                new MockSmsGateway());
        telecom = new TelecomService(new TelecomSessionRepository(jdbc, om), workers, jobs, wage);
    }

    // ------------------------------------------------------------------ helpers (Python rounding)

    @Test
    void pythonStyleRoundingIsReproduced() {
        assertEquals(2.67, PyMath.round(2.675, 2));      // Python: round(2.675, 2) == 2.67
        assertEquals(2.0, PyMath.roundToWhole(2.5));     // ties go to the even number
        assertEquals(4.0, PyMath.roundToWhole(3.5));
        assertEquals("1,250", Fmt.commaF0(1250));
        assertEquals("850", Fmt.f0(850.4));
    }

    // ------------------------------------------------------------------ location

    @Test
    void haversineDistances() {
        assertEquals(128.02, location.haversineDistance(12.9716, 77.5946, 12.2958, 76.6394));
        assertTrue(location.haversineDistance(12.9716, 77.5946, 28.6139, 77.2090) > 1700);
        ResolvedCity hubballi = location.resolveCityCoords("Hubballi");
        assertEquals("Tier-2", hubballi.tier());
        assertTrue(hubballi.lat() > 15.0 && hubballi.lat() < 16.0);
        // unknown city falls back to Bengaluru's centre
        assertEquals(12.9716, location.resolveCityCoords("Nowhere").lat());
        // "bangalore" and "bengaluru" are listed once
        assertEquals(1, location.listSupportedCities().stream().filter(c -> c.name().equals("Bengaluru")).count());
    }

    // ------------------------------------------------------------------ wage

    @Test
    void fairWageMatchesPythonOutput() {
        FairWageResponse r = wage.predictFairWage("Electrician", "Bengaluru", 5, true, false, 8, null);
        assertEquals("Electrician", r.trade());
        assertEquals("Tier-1", r.cityTier());
        assertEquals(1450.0, r.recommendedDailyWage());
        assertEquals(181.2, r.recommendedHourlyWage());
        assertEquals(1250.0, r.fairWageMin());
        assertEquals(1800.0, r.fairWageMax());
        assertEquals("FAIR", r.wageBracket());
        assertTrue(r.regionalExplanations().get("kannada").contains("ದಿನಗೂಲಿ"));
        assertTrue(r.regionalExplanations().get("hindi").contains("मजदूरी"));
    }

    @Test
    void wageBracketJudgesAnEmployerOffer() {
        assertEquals("BELOW_FAIR_WAGE", wage.predictFairWage("Electrician", "Bengaluru", 5, true, false, 8, 500.0).wageBracket());
        assertEquals("FAIR_MARKET_RATE", wage.predictFairWage("Electrician", "Bengaluru", 5, true, false, 8, 1500.0).wageBracket());
        assertEquals("PREMIUM_OFFER", wage.predictFairWage("Electrician", "Bengaluru", 5, true, false, 8, 3000.0).wageBracket());
    }

    // ------------------------------------------------------------------ workers / jobs

    @Test
    void seedDataIsLoaded() {
        assertEquals(10, workers.listWorkers(null, null, null, null, null, 100, 0).size());
        assertEquals(8, jobs.listJobs(null, null, "open", null, null, 100, 0).size());
    }

    @Test
    void workerRegistrationFillsDefaultsAndLooksUpCoordinates() {
        Worker w = workers.createWorker(new WorkerCreateRequest("Sharanu Gowda", "9845999001", "Plumber", null, 4,
                750.0, null, "Mysuru", "", null, null, null, null, null));
        assertEquals(93.8, w.hourlyRate());            // 750 / 8, rounded to 1 decimal
        assertEquals("Mysuru", w.area());              // empty area -> city
        assertEquals(12.2958, w.latitude());           // from the city table
        assertEquals("available", w.availability());
        assertTrue(workers.getWorkerByPhone("9845999001").isPresent());
    }

    @Test
    void partialUpdateOnlyTouchesSentFields() {
        Worker updated = workers.updateWorker(1, new WorkerUpdateRequest(null, null, null, null, 999.0,
                null, null, null, null, null, "busy", null)).orElseThrow();
        assertEquals(999.0, updated.dailyRate());
        assertEquals("busy", updated.availability());
        assertEquals("Ramesh Gowda", updated.name());
        assertTrue(workers.updateWorker(9999, new WorkerUpdateRequest(null, "x", null, null, null,
                null, null, null, null, null, null, null)).isEmpty());
    }

    // ------------------------------------------------------------------ matching

    @Test
    void scoringMatchesPythonOutput() {
        Worker ramesh = workers.getWorker(1).orElseThrow();
        Job shop = jobs.getJob(1).orElseThrow();
        MatchScoreBreakdown s = matching.scoreMatch(ramesh, shop, 50.0);
        assertEquals(99.8, s.totalScore());
        assertEquals(0.12, s.distanceKm());
        assertEquals(97.6, s.experienceScore());
        assertEquals("Strong skill match (Electrician); 0.12 km away (Rajajinagar); Fair pay (₹1800 >= ₹850 requested)",
                s.explanation());
    }

    @Test
    void jobsForWorkerAreRankedBestFirst() {
        List<JobMatch> m = matching.matchJobsForWorker(1, 100.0, 40.0, 15);
        assertEquals(List.of(1L, 8L, 3L, 2L), m.stream().map(x -> x.job().id()).limit(4).toList());
        assertTrue(m.get(0).matchScore() >= m.get(1).matchScore());
        assertTrue(matching.matchJobsForWorker(9999, 50.0, 40.0, 15).isEmpty());
    }

    // ------------------------------------------------------------------ contact

    @Test
    void contactFlowAndSmsSimulation() {
        ContactResponse c = contacts.createContact("employer", 1L, "worker", 1L, 1L,
                "We have an electrical wiring project.");
        assertEquals("pending", c.status());
        assertEquals("Shree Manjunatha Traders", c.senderName());   // employer details come from the job
        assertEquals("Ramesh Gowda", c.receiverName());

        SmsDispatchResult sms = contacts.dispatchSms(c.id()).orElseThrow();
        assertEquals("SENT", sms.dispatchStatus());
        assertEquals("MockSmsGateway", sms.provider());
        assertTrue(sms.notificationText().contains("KaayakaSetu"));

        assertEquals("accepted", contacts.updateStatus(c.id(), new ContactStatusUpdate("accepted")).orElseThrow().status());
        assertTrue(contacts.getContactWithDetails(9999).isEmpty());
    }

    @Test
    void smsGoesThroughWhateverGatewayIsPluggedIn() {
        // a hand-made gateway: proves ContactService only knows the SmsGateway interface
        List<String> outbox = new ArrayList<>();
        SmsGateway recording = new SmsGateway() {
            @Override public SmsSendResult send(String to, String text) {
                outbox.add(to + "|" + text);
                return SmsSendResult.sent(name(), "id-42");
            }
            @Override public String name() { return "RecordingGateway"; }
        };
        ContactService svc = new ContactService(new ContactRepository(jdbc), workers, jobs, new UserRepository(jdbc), recording);
        ContactResponse c = svc.createContact("employer", 1L, "worker", 1L, 1L, "Wiring work");
        SmsDispatchResult r = svc.dispatchSms(c.id()).orElseThrow();
        assertEquals("RecordingGateway", r.provider());
        assertEquals("id-42", r.providerMessageId());
        assertEquals(1, outbox.size());
        assertTrue(outbox.get(0).startsWith("9845011223|KaayakaSetu"));   // goes to the worker's phone

        SmsGateway failing = new SmsGateway() {
            @Override public SmsSendResult send(String to, String text) { return SmsSendResult.failed(name(), "boom"); }
            @Override public String name() { return "FailingGateway"; }
        };
        ContactService svc2 = new ContactService(new ContactRepository(jdbc), workers, jobs, new UserRepository(jdbc), failing);
        SmsDispatchResult bad = svc2.dispatchSms(c.id()).orElseThrow();
        assertEquals("FAILED", bad.dispatchStatus());
        assertEquals("boom", bad.error());
    }

    // ------------------------------------------------------------------ USSD / IVR

    @Test
    void ussdKannadaRegistrationFlow() {
        String sid = "t_kn";
        String phone = "9845000111";
        TelecomService.UssdResult r = telecom.handleUssd(sid, phone, "*345#");
        assertEquals("CON", r.action());
        assertTrue(r.text().contains("1. ಕನ್ನಡ"));

        r = telecom.handleUssd(sid, phone, "1");
        assertTrue(r.text().contains("ಕಾಯಕಸೇತು ಮುಖ್ಯ ಮೆನು"));
        r = telecom.handleUssd(sid, phone, "1");
        assertTrue(r.text().contains("ಕೌಶಲ್ಯ/ವೃತ್ತಿ ಆಯ್ಕೆ ಮಾಡಿ"));
        r = telecom.handleUssd(sid, phone, "2");           // Electrician
        assertTrue(r.text().contains("ಅನುಭವ ಎಷ್ಟು ವರ್ಷ"));
        r = telecom.handleUssd(sid, phone, "5");
        assertTrue(r.text().contains("ದೈನಂದಿನ ಕೂಲಿ"));
        r = telecom.handleUssd(sid, phone, "850");
        assertTrue(r.text().contains("ನಗರ/ಜಿಲ್ಲೆ ಆಯ್ಕೆ ಮಾಡಿ"));
        r = telecom.handleUssd(sid, phone, "1");           // Bengaluru
        assertEquals("END", r.action());
        assertTrue(r.text().contains("ಧನ್ಯವಾದಗಳು! ನಿಮ್ಮ ಪ್ರೊಫೈಲ್ ಯಶಸ್ವಿಯಾಗಿ ನೋಂದಾಯಿಸಲಾಗಿದೆ"));

        Worker saved = workers.getWorkerByPhone(phone).orElseThrow();
        assertEquals("Electrician", saved.trade());
        assertEquals("ussd", saved.source());
        assertEquals(850.0, saved.dailyRate());
    }

    @Test
    void ussdHindiFindJobsFlowShowsPythonText() {
        String sid = "t_hi";
        telecom.handleUssd(sid, "9811000222", "*345#");
        TelecomService.UssdResult r = telecom.handleUssd(sid, "9811000222", "2");
        assertTrue(r.text().contains("श्रमसेतु मुख्य मेन्यू") && r.text().contains("2. नजदीकी काम खोजें"));
        r = telecom.handleUssd(sid, "9811000222", "2");
        assertTrue(r.text().contains("हुनर चुनें"));
        r = telecom.handleUssd(sid, "9811000222", "2");    // Electrician
        assertEquals("END", r.action());
        assertEquals("उपलब्ध Electrician काम (सीधे कॉल करें):\n"
                + "1. Commercial Shop 3-Ph | Rajajinagar | ₹1800/ದಿನ | Ph: 9900112233\n"
                + "2. Factory Main Line In | Okhla | ₹1000/ದಿನ | Ph: 9810112233", r.text());
    }

    @Test
    void ussdInvalidRegistrationNumbersAreSwallowedLikeTheOriginal() {
        String sid = "t_bad";
        String phone = "9845000222";
        for (String in : new String[]{"*345#", "3", "1", "1", "-3", "0"}) {
            telecom.handleUssd(sid, phone, in);
        }
        TelecomService.UssdResult r = telecom.handleUssd(sid, phone, "2");
        assertEquals("END", r.action());
        assertEquals("Registration complete. Welcome to KaayakaSetu.", r.text());
        assertTrue(workers.getWorkerByPhone(phone).isEmpty());   // nothing was actually saved
    }

    @Test
    void ivrBilingualFlow() {
        IvrResponse hello = telecom.handleIvrCall("CALL_1", "9845011999", "");
        assertEquals("gather", hello.action());
        assertTrue(hello.promptText().contains("ಕಾಯಕಸೇತುವಿಗೆ ಸ್ವಾಗತ"));
        assertTrue(hello.promptText().contains("श्रमसेतु में आपका स्वागत है"));
        assertTrue(hello.twimlXml().startsWith("<?xml") && hello.twimlXml().contains("<Gather"));

        IvrResponse reg = telecom.handleIvrCall("CALL_1", "9845011999", "1");
        assertEquals("hangup", reg.action());
        assertTrue(reg.promptText().contains("ನೋಂದಾಯಿಸಲಾಗಿದೆ"));
        assertTrue(reg.promptText().contains("पंजीकृत"));
        assertEquals("ivr", workers.getWorkerByPhone("9845011999").orElseThrow().source());

        assertTrue(telecom.handleIvrCall("CALL_1", "9845011999", "2").promptText().contains("Commercial Shop"));
        assertEquals("hangup", telecom.handleIvrCall("CALL_1", "9845011999", "9").action());
    }
}
