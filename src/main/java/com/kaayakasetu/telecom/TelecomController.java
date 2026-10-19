package com.kaayakasetu.telecom;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** USSD + IVR webhooks. The JSON endpoints feed the dashboard's phone simulator; the form endpoints are for real providers. */
@RestController
@RequestMapping("/api/v1/telecom")
@Tag(name = "IVR & USSD Regional Language Microservice")
public class TelecomController {

    private static final MediaType TEXT_PLAIN_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);
    private static final MediaType XML_UTF8 = new MediaType("application", "xml", StandardCharsets.UTF_8);

    private final TelecomService telecomService;

    public TelecomController(TelecomService telecomService) {
        this.telecomService = telecomService;
    }

    /** USSD gateway (JSON). The message is prefixed with the action word, e.g. "CON Select Language...". */
    @PostMapping("/ussd")
    public UssdResponse ussd(@Valid @RequestBody UssdRequest req) {
        TelecomService.UssdResult r = telecomService.handleUssd(req.sessionId(), req.phoneNumber(), req.userInput());
        return new UssdResponse(req.sessionId(), r.action() + " " + r.text(), r.action());
    }

    /**
     * Africa's Talking / Exotel style webhook: form fields sessionId, phoneNumber, text ("1*2*4" = all keys so far).
     * Only the latest key press is used, and the reply is plain text: "CON ..." or "END ...".
     */
    @PostMapping("/ussd/form")
    public ResponseEntity<String> ussdForm(@RequestParam("sessionId") String sessionId,
                                           @RequestParam("phoneNumber") String phoneNumber,
                                           @RequestParam(name = "text", defaultValue = "") String text) {
        String latest = text.contains("*") ? text.substring(text.lastIndexOf('*') + 1) : text;
        TelecomService.UssdResult r = telecomService.handleUssd(sessionId, phoneNumber, latest);
        return ResponseEntity.ok().contentType(TEXT_PLAIN_UTF8).body(r.action() + " " + r.text());
    }

    /** IVR (JSON) for the simulator. */
    @PostMapping("/ivr")
    public IvrResponse ivr(@Valid @RequestBody IvrRequest req) {
        return telecomService.handleIvrCall(req.callSid(), req.callerPhone(), req.digits());
    }

    /** Twilio / Exotel voice webhook: form fields CallSid, From, Digits; reply is TwiML (XML). */
    @PostMapping("/ivr/twiml")
    public ResponseEntity<String> ivrTwiml(@RequestParam("CallSid") String callSid,
                                           @RequestParam("From") String from,
                                           @RequestParam(name = "Digits", required = false) String digits) {
        IvrResponse res = telecomService.handleIvrCall(callSid, from, digits);
        return ResponseEntity.ok().contentType(XML_UTF8).body(res.twimlXml());
    }

    /** Manually clear a USSD session. */
    @PostMapping("/ussd/reset")
    public Map<String, Object> reset(@RequestParam("session_id") String sessionId) {
        telecomService.deleteSession(sessionId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "success");
        body.put("session_id", sessionId);
        body.put("message", "Session reset");
        return body;
    }
}
