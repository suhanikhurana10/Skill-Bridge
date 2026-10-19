package com.kaayakasetu.telecom;

import com.kaayakasetu.common.Fmt;
import com.kaayakasetu.job.Job;
import com.kaayakasetu.job.JobService;
import com.kaayakasetu.wage.FairWageResponse;
import com.kaayakasetu.wage.FairWageService;
import com.kaayakasetu.worker.Worker;
import com.kaayakasetu.worker.WorkerCreateRequest;
import com.kaayakasetu.worker.WorkerService;
import com.kaayakasetu.worker.WorkerUpdateRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Port of ivr_ussd_service.py.
 *
 * USSD  = the "*345#" menu on a feature phone. Text only, max ~160 chars per screen, one key press per HTTP call.
 *         Implemented as a STATE MACHINE: the session row remembers the current step, each input moves it on.
 * IVR   = an automated voice call. We answer with TwiML (XML) which Twilio/Exotel turn into speech.
 */
@Service
public class TelecomService {

    private static final Logger log = LoggerFactory.getLogger(TelecomService.class);

    /** Result of one USSD step: action is "CON" (continue) or "END". */
    public record UssdResult(String action, String text) {}

    private record Option(String value, String kn, String hi) {}

    private static final Map<String, Option> TRADE_OPTIONS = new LinkedHashMap<>();
    private static final Map<String, Option> CITY_OPTIONS = new LinkedHashMap<>();

    static {
        TRADE_OPTIONS.put("1", new Option("Mason", "ಮೇಸ್ತ್ರಿ (Mason)", "राजमिस्त्री (Mason)"));
        TRADE_OPTIONS.put("2", new Option("Electrician", "ಎಲೆಕ್ಟ್ರಿಷಿಯನ್ (Electrician)", "इलेक्ट्रीशियन (Electrician)"));
        TRADE_OPTIONS.put("3", new Option("Plumber", "ಪ್ಲಂಬರ್ (Plumber)", "प्लंबर (Plumber)"));
        TRADE_OPTIONS.put("4", new Option("Carpenter", "ಬಡಗಿ (Carpenter)", "बढ़ई (Carpenter)"));
        TRADE_OPTIONS.put("5", new Option("Painter", "ಪೇಂಟರ್ (Painter)", "पेंटर (Painter)"));
        TRADE_OPTIONS.put("6", new Option("Helper", "ಕೂಲಿ ಕಾರ್ಮಿಕ (Helper)", "मजदूर (Helper)"));

        CITY_OPTIONS.put("1", new Option("Bengaluru", "ಬೆಂಗಳೂರು (Bengaluru)", "बेंगलुरु (Bengaluru)"));
        CITY_OPTIONS.put("2", new Option("Mysuru", "ಮೈಸೂರು (Mysuru)", "मैसूर (Mysuru)"));
        CITY_OPTIONS.put("3", new Option("Hubballi", "ಹುಬ್ಬಳ್ಳಿ (Hubballi)", "हुबली (Hubballi)"));
        CITY_OPTIONS.put("4", new Option("Delhi", "ದೆಹಲಿ (Delhi)", "दिल्ली (Delhi)"));
        CITY_OPTIONS.put("5", new Option("Lucknow", "ಲಕ್ನೋ (Lucknow)", "लखनऊ (Lucknow)"));
        CITY_OPTIONS.put("6", new Option("Patna", "ಪಾಟ್ನಾ (Patna)", "पटना (Patna)"));
    }

    private static final String WELCOME =
            "KaayakaSetu / ಕಾಯಕಸೇತು / श्रमसेतु\nSelect Language / ಭಾಷೆ ಆಯ್ಕೆ / भाषा चुनें:\n1. ಕನ್ನಡ (Kannada)\n2. हिन्दी (Hindi)\n3. English";
    private static final String MENU_KANNADA =
            "ಕಾಯಕಸೇತು ಮುಖ್ಯ ಮೆನು:\n1. ಕಾರ್ಮಿಕ ಪ್ರೊಫೈಲ್ ನೋಂದಣಿ\n2. ಹತ್ತಿರದ ಕೆಲಸ ಹುಡುಕಿ\n3. ನ್ಯಾಯಯುತ ದಿನಗೂಲಿ ದರ ತಿಳಿಯಿರಿ";
    private static final String MENU_HINDI =
            "श्रमसेतु मुख्य मेन्यू:\n1. श्रमिक प्रोफ़ाइल बनाएं\n2. नजदीकी काम खोजें\n3. उचित मजदूरी दर जांचें";
    private static final String MENU_ENGLISH =
            "KaayakaSetu Menu:\n1. Register Worker Profile\n2. Find Matching Jobs\n3. Check Fair Daily Wage";

    private final TelecomSessionRepository sessions;
    private final WorkerService workerService;
    private final JobService jobService;
    private final FairWageService fairWageService;

    public TelecomService(TelecomSessionRepository sessions, WorkerService workerService,
                          JobService jobService, FairWageService fairWageService) {
        this.sessions = sessions;
        this.workerService = workerService;
        this.jobService = jobService;
        this.fairWageService = fairWageService;
    }

    public void deleteSession(String sessionId) {
        sessions.delete(sessionId);
    }

    // =========================================================================================== USSD

    public UssdResult handleUssd(String sessionId, String phone, String rawInput) {
        String input = rawInput == null ? "" : rawInput.strip();
        TelecomSession session = sessions.find(sessionId).orElseGet(() -> sessions.create(sessionId, phone, "ussd"));
        String step = session.currentStep();
        String lang = session.language();
        Map<String, Object> draft = session.draft();

        // First dial (*345#) or empty input -> language menu
        if (step.equals("welcome") && (input.isEmpty() || input.startsWith("*"))) {
            return new UssdResult("CON", WELCOME);
        }

        // Step 1: language selection (anything other than 1 or 2 means English)
        if (step.equals("welcome")) {
            if (input.equals("1")) {
                sessions.update(sessionId, "main_menu", "kannada", draft);
                return new UssdResult("CON", MENU_KANNADA);
            } else if (input.equals("2")) {
                sessions.update(sessionId, "main_menu", "hindi", draft);
                return new UssdResult("CON", MENU_HINDI);
            } else {
                sessions.update(sessionId, "main_menu", "english", draft);
                return new UssdResult("CON", MENU_ENGLISH);
            }
        }

        // Step 2: main menu
        if (step.equals("main_menu")) {
            switch (input) {
                case "1":
                    sessions.update(sessionId, "reg_trade", lang, draft);
                    return new UssdResult("CON", pick(lang,
                            "ನಿಮ್ಮ ಕೌಶಲ್ಯ/ವೃತ್ತಿ ಆಯ್ಕೆ ಮಾಡಿ:\n" + options(TRADE_OPTIONS, lang),
                            "अपना हुनर/व्यवसाय चुनें:\n" + options(TRADE_OPTIONS, lang),
                            "Select your trade/skill:\n" + options(TRADE_OPTIONS, lang)));
                case "2":
                    sessions.update(sessionId, "find_jobs_trade", lang, draft);
                    return new UssdResult("CON", pick(lang,
                            "ಯಾವ ಕೆಲಸ ಹುಡುಕುತ್ತಿದ್ದೀರಿ? ಕೌಶಲ್ಯ ಆಯ್ಕೆ ಮಾಡಿ:\n" + options(TRADE_OPTIONS, lang),
                            "किस काम की तलाश है? हुनर चुनें:\n" + options(TRADE_OPTIONS, lang),
                            "Select skill to find jobs:\n" + options(TRADE_OPTIONS, lang)));
                case "3":
                    sessions.update(sessionId, "wage_trade", lang, draft);
                    return new UssdResult("CON", pick(lang,
                            "ನ್ಯಾಯಯುತ ದರ ತಿಳಿಯಲು ವೃತ್ತಿ ಆಯ್ಕೆ ಮಾಡಿ:\n" + options(TRADE_OPTIONS, lang),
                            "उचित दर जानने के लिए व्यवसाय चुनें:\n" + options(TRADE_OPTIONS, lang),
                            "Select trade for wage enquiry:\n" + options(TRADE_OPTIONS, lang)));
                default:
                    return new UssdResult("CON", "Invalid option. Please enter 1, 2, or 3.");
            }
        }

        // ---------------------------------------------------------------- registration sub-flow
        if (step.equals("reg_trade")) {
            Option selected = TRADE_OPTIONS.getOrDefault(input, TRADE_OPTIONS.get("1"));
            draft.put("trade", selected.value());
            sessions.update(sessionId, "reg_exp", lang, draft);
            return new UssdResult("CON", pick(lang,
                    "ನಿಮ್ಮ ಒಟ್ಟು ಕೆಲಸದ ಅನುಭವ ಎಷ್ಟು ವರ್ಷ? (ಸಂಖ್ಯೆ ನಮೂದಿಸಿ, ಉದಾ: 4)",
                    "काम का कुल अनुभव कितने वर्ष है? (संख्या दर्ज करें, जैसे: 4)",
                    "Enter your experience in years (e.g., 4):"));
        }

        if (step.equals("reg_exp")) {
            int exp;
            try {
                exp = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                exp = 3;
            }
            draft.put("experience_years", exp);
            sessions.update(sessionId, "reg_wage", lang, draft);
            return new UssdResult("CON", pick(lang,
                    "ನಿಮ್ಮ ನಿರೀಕ್ಷಿತ ದೈನಂದಿನ ಕೂಲಿ ಎಷ್ಟು? (ರೂಪಾಯಿಗಳಲ್ಲಿ ನಮೂದಿಸಿ, ಉದಾ: 800)",
                    "आपकी अपेक्षित दैनिक मजदूरी कितनी है? (रुपये में दर्ज करें, जैसे: 800)",
                    "Enter your expected daily wage in INR (e.g., 800):"));
        }

        if (step.equals("reg_wage")) {
            double wage;
            try {
                wage = Double.parseDouble(input);
            } catch (NumberFormatException e) {
                wage = 800.0;
            }
            draft.put("daily_rate", wage);
            sessions.update(sessionId, "reg_city", lang, draft);
            return new UssdResult("CON", pick(lang,
                    "ನಿಮ್ಮ ನಗರ/ಜಿಲ್ಲೆ ಆಯ್ಕೆ ಮಾಡಿ:\n" + options(CITY_OPTIONS, lang),
                    "अपना शहर चुनें:\n" + options(CITY_OPTIONS, lang),
                    "Select your city:\n" + options(CITY_OPTIONS, lang)));
        }

        if (step.equals("reg_city")) {
            Option cityData = CITY_OPTIONS.getOrDefault(input, CITY_OPTIONS.get("1"));
            draft.put("city", cityData.value());

            String trade = (String) draft.get("trade");
            int experience = ((Number) draft.get("experience_years")).intValue();
            double dailyRate = ((Number) draft.get("daily_rate")).doubleValue();
            String city = cityData.value();

            try {
                // The Python version validated through Pydantic here (experience 0..50, wage > 0); a failure was
                // swallowed by the catch below. We keep that behaviour so both versions answer identically.
                if (experience < 0 || experience > 50 || dailyRate <= 0) {
                    throw new IllegalArgumentException("invalid registration values");
                }
                Optional<Worker> existing = workerService.getWorkerByPhone(phone);
                if (existing.isPresent()) {
                    workerService.updateWorker(existing.get().id(), new WorkerUpdateRequest(
                            null, trade, null, experience, dailyRate, null, city, null, null, null, null, lang));
                } else {
                    workerService.createWorker(new WorkerCreateRequest(
                            "Worker " + last4(phone), phone, trade, null, experience, dailyRate, null,
                            city, null, null, null, null, lang, "ussd"));
                }
                sessions.delete(sessionId);

                return new UssdResult("END", pick(lang,
                        "ಧನ್ಯವಾದಗಳು! ನಿಮ್ಮ ಪ್ರೊಫೈಲ್ ಯಶಸ್ವಿಯಾಗಿ ನೋಂದಾಯಿಸಲಾಗಿದೆ.\n"
                                + "ವೃತ್ತಿ: " + trade + "\nದಿನಗೂಲಿ: ₹" + Fmt.f0(dailyRate) + "\n"
                                + "ನಗರ: " + city + "\nಉದ್ಯೋಗದಾತರು ಶೀಘ್ರದಲ್ಲೇ ಸಂಪರ್ಕಿಸುತ್ತಾರೆ.",
                        "धन्यवाद! आपकी प्रोफ़ाइल सफलतापूर्वक दर्ज हो गई है।\n"
                                + "व्यवसाय: " + trade + "\nमजदूरी: ₹" + Fmt.f0(dailyRate) + "\n"
                                + "शहर: " + city + "\nनियोक्ता आपसे जल्द संपर्क करेंगे।",
                        "Success! Worker profile registered.\n"
                                + "Trade: " + trade + ", Rate: ₹" + Fmt.f0(dailyRate) + "/day, City: " + city + ".\n"
                                + "Employers will contact you shortly."));
            } catch (Exception e) {
                log.error("Error registering worker via USSD: {}", e.getMessage());
                sessions.delete(sessionId);
                return new UssdResult("END", "Registration complete. Welcome to KaayakaSetu.");
            }
        }

        // ---------------------------------------------------------------- find-jobs sub-flow
        if (step.equals("find_jobs_trade")) {
            String trade = TRADE_OPTIONS.getOrDefault(input, TRADE_OPTIONS.get("1")).value();
            List<Job> jobs = jobService.listJobs(trade, null, "open", null, null, 3, 0);
            sessions.delete(sessionId);

            if (jobs.isEmpty()) {
                return new UssdResult("END", pick(lang,
                        "ಕ್ಷಮಿಸಿ, " + trade + " ವೃತ್ತಿಗೆ ಸದ್ಯದಲ್ಲಿ ಹೊಸ ಕೆಲಸಗಳು ಲಭ್ಯವಿಲ್ಲ. ದಯವಿಟ್ಟು ನಂತರ ಪ್ರಯತ್ನಿಸಿ.",
                        "क्षमा करें, " + trade + " के लिए अभी कोई खुला काम उपलब्ध नहीं है। कृपया बाद में प्रयास करें।",
                        "No open jobs currently found for " + trade + ". Please check back soon."));
            }

            List<String> lines = new ArrayList<>();
            int idx = 1;
            for (Job j : jobs) {
                String area = (j.area() == null || j.area().isEmpty()) ? j.city() : j.area();
                // NB: "/ದಿನ" (per day, in Kannada) is shown in every language - same as the original
                lines.add(idx + ". " + truncate(j.title(), 20) + " | " + area + " | ₹" + Fmt.f0(j.wageOffered())
                        + "/ದಿನ | Ph: " + j.employerPhone());
                idx++;
            }
            String body = String.join("\n", lines);
            return new UssdResult("END", pick(lang,
                    "ಲಭ್ಯವಿರುವ " + trade + " ಕೆಲಸಗಳು (ನೇರವಾಗಿ ಕರೆ ಮಾಡಿ):\n" + body,
                    "उपलब्ध " + trade + " काम (सीधे कॉल करें):\n" + body,
                    "Matching " + trade + " Jobs (Call directly):\n" + body));
        }

        // ---------------------------------------------------------------- fair-wage sub-flow
        if (step.equals("wage_trade")) {
            String trade = TRADE_OPTIONS.getOrDefault(input, TRADE_OPTIONS.get("1")).value();
            sessions.delete(sessionId);

            FairWageResponse pred = fairWageService.predictFairWage(trade, "Bengaluru", 4);
            String daily = Fmt.f0(pred.recommendedDailyWage());
            String lo = Fmt.f0(pred.fairWageMin());
            String hi = Fmt.f0(pred.fairWageMax());
            return new UssdResult("END", pick(lang,
                    "ನ್ಯಾಯಯುತ ದರ (" + trade + "):\nದೈನಂದಿನ ಕೂಲಿ: ₹" + daily + "\nಸೂಕ್ತ ಶ್ರೇಣಿ: ₹" + lo + " - ₹" + hi + "/ದಿನ.",
                    "उचित मजदूरी (" + trade + "):\nदैनिक मजदूरी: ₹" + daily + "\nउचित दायरा: ₹" + lo + " - ₹" + hi + "/दिन.",
                    "Fair Market Wage (" + trade + "):\nRecommended: ₹" + daily + "/day\nRange: ₹" + lo + " - ₹" + hi + "/day."));
        }

        // Unknown step -> reset
        sessions.delete(sessionId);
        return new UssdResult("END", "Session expired. Dial *345# again.");
    }

    // =========================================================================================== IVR

    public IvrResponse handleIvrCall(String callSid, String phone, String rawDigits) {
        String digits = rawDigits == null ? "" : rawDigits.strip();

        // Greeting: press 1 to register, 2 to hear jobs
        if (digits.isEmpty()) {
            String kn = "ನಮಸ್ಕಾರ, ಕಾಯಕಸೇತುವಿಗೆ ಸ್ವಾಗತ. "
                    + "ಕಾರ್ಮಿಕ ಪ್ರೊಫೈಲ್ ನೋಂದಾಯಿಸಲು 1 ಒತ್ತಿ. "
                    + "ಇಂದಿನ ಹೊಸ ಕೆಲಸಗಳ ಮಾಹಿತಿ ಕೇಳಲು 2 ಒತ್ತಿ.";
            String hi = "नमस्कार, श्रमसेतु में आपका स्वागत है। "
                    + "श्रमिक प्रोफ़ाइल दर्ज करने के लिए 1 दबाएं। "
                    + "आज के नए काम सुनने के लिए 2 दबाएं।";
            String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<Response>\n"
                    + "    <Gather numDigits=\"1\" action=\"/api/v1/telecom/ivr\" method=\"POST\" timeout=\"10\">\n"
                    + "        <Say language=\"kn-IN\" voice=\"Polly.Aditi\">" + xml(kn) + "</Say>\n"
                    + "        <Say language=\"hi-IN\" voice=\"Polly.Aditi\">" + xml(hi) + "</Say>\n"
                    + "    </Gather>\n"
                    + "</Response>";
            List<Map<String, String>> options = List.of(
                    Map.of("digit", "1", "label", "ಕಾರ್ಮಿಕ ನೋಂದಣಿ / श्रमिक पंजीकरण (Register Profile)"),
                    Map.of("digit", "2", "label", "ಹೊಸ ಕೆಲಸಗಳು / नए काम खोजें (Find Jobs)"));
            return new IvrResponse(callSid, twiml, "[KN]: " + kn + "\n[HI]: " + hi, kn + ". " + hi,
                    "bilingual", "gather", options);
        }

        // 1 -> quick registration from caller ID
        if (digits.equals("1")) {
            if (workerService.getWorkerByPhone(phone).isEmpty()) {
                workerService.createWorker(new WorkerCreateRequest(
                        "Worker " + last4(phone), phone, "Helper", null, 2, 650.0, null,
                        "Bengaluru", null, null, null, null, "kannada", "ivr"));
            }
            String kn = "ಧನ್ಯವಾದಗಳು! ನಿಮ್ಮ ಮೊಬೈಲ್ ಸಂಖ್ಯೆಯನ್ನು ಕಾಯಕಸೇತುವಿನಲ್ಲಿ ಯಶಸ್ವಿಯಾಗಿ ನೋಂದಾಯಿಸಲಾಗಿದೆ. ಉದ್ಯೋಗದಾತರು ಶೀಘ್ರದಲ್ಲೇ ಸಂಪರ್ಕಿಸುತ್ತಾರೆ.";
            String hi = "धन्यवाद! आपका मोबाइल नंबर श्रमसेतु पर पंजीकृत हो गया है। नियोक्ता आपसे जल्द संपर्क करेंगे।";
            return hangup(callSid, kn, hi);
        }

        // 2 -> read out the newest open job
        if (digits.equals("2")) {
            List<Job> jobs = jobService.listJobs(null, null, "open", null, null, 2, 0);
            String kn;
            String hi;
            if (!jobs.isEmpty()) {
                Job j1 = jobs.get(0);
                kn = "ಇಂದಿನ ಕೆಲಸ: " + j1.title() + ", ದಿನಗೂಲಿ ₹" + Fmt.f0(j1.wageOffered())
                        + ". ಸಂಪರ್ಕ ಸಂಖ್ಯೆ " + j1.employerPhone() + ".";
                hi = "आज का काम: " + j1.title() + ", दैनिक मजदूरी ₹" + Fmt.f0(j1.wageOffered())
                        + ". संपर्क नंबर " + j1.employerPhone() + ".";
            } else {
                kn = "ಸದ್ಯಕ್ಕೆ ಹೊಸ ಕೆಲಸಗಳು ಲಭ್ಯವಿಲ್ಲ. ಧನ್ಯವಾದಗಳು.";
                hi = "फिलहाल कोई नया काम उपलब्ध नहीं है। धन्यवाद।";
            }
            return hangup(callSid, kn, hi);
        }

        // anything else
        String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Response>\n"
                + "    <Say language=\"kn-IN\">ತಪ್ಪಾದ ಆಯ್ಕೆ. ದಯವಿಟ್ಟು ಮರಳಿ ಕರೆ ಮಾಡಿ.</Say>\n"
                + "    <Hangup/>\n"
                + "</Response>";
        return new IvrResponse(callSid, twiml, "Invalid selection / ತಪ್ಪಾದ ಆಯ್ಕೆ / अमान्य विकल्प",
                "ತಪ್ಪಾದ ಆಯ್ಕೆ. ದಯವಿಟ್ಟು ಮರಳಿ ಕರೆ ಮಾಡಿ.", "kannada", "hangup", List.of());
    }

    private IvrResponse hangup(String callSid, String kn, String hi) {
        String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Response>\n"
                + "    <Say language=\"kn-IN\" voice=\"Polly.Aditi\">" + xml(kn) + "</Say>\n"
                + "    <Say language=\"hi-IN\" voice=\"Polly.Aditi\">" + xml(hi) + "</Say>\n"
                + "    <Hangup/>\n"
                + "</Response>";
        return new IvrResponse(callSid, twiml, "[KN]: " + kn + "\n[HI]: " + hi, kn + ". " + hi,
                "bilingual", "hangup", List.of());
    }

    // =========================================================================================== helpers

    /** Picks the text for the caller's language: Kannada, Hindi, or (anything else) English. */
    private static String pick(String lang, String kannada, String hindi, String english) {
        if ("kannada".equals(lang)) return kannada;
        if ("hindi".equals(lang)) return hindi;
        return english;
    }

    /** "1. label\n2. label ..." in the caller's language. */
    private static String options(Map<String, Option> opts, String lang) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Option> e : opts.entrySet()) {
            String label = pick(lang, e.getValue().kn(), e.getValue().hi(), e.getValue().value());
            lines.add(e.getKey() + ". " + label);
        }
        return String.join("\n", lines);
    }

    private static String last4(String phone) {
        return phone.length() <= 4 ? phone : phone.substring(phone.length() - 4);
    }

    /** First n characters (by code point, so Indic text is never cut in half a character). */
    private static String truncate(String s, int n) {
        if (s.codePointCount(0, s.length()) <= n) return s;
        return s.substring(0, s.offsetByCodePoints(0, n));
    }

    /** Escapes text placed inside the TwiML XML (a job title such as "A & B" would otherwise break the XML). */
    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
