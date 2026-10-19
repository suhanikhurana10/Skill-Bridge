package com.kaayakasetu.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real SMS through Twilio's REST API. Active only when sms.provider=twilio.
 * It calls the HTTP API directly (POST /2010-04-01/Accounts/{sid}/Messages.json with HTTP Basic auth),
 * so no extra library is needed.
 */
@Component
@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "twilio")
public class TwilioSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsGateway.class);
    static final String BASE_URL = "https://api.twilio.com";

    private final RestClient client;
    private final String accountSid;
    private final String fromNumber;
    private final String defaultCountryCode;
    private final ObjectMapper objectMapper;

    @Autowired
    public TwilioSmsGateway(@Value("${sms.twilio.account-sid:}") String accountSid,
                            @Value("${sms.twilio.auth-token:}") String authToken,
                            @Value("${sms.twilio.from-number:}") String fromNumber,
                            @Value("${sms.default-country-code:+91}") String defaultCountryCode,
                            ObjectMapper objectMapper) {
        this(buildClient(accountSid, authToken), accountSid, fromNumber, defaultCountryCode, objectMapper);
        if (isBlank(accountSid) || isBlank(authToken) || isBlank(fromNumber)) {
            // fail at startup with a clear message instead of failing on the first SMS
            throw new IllegalStateException("sms.provider=twilio needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN "
                    + "and TWILIO_FROM_NUMBER to be set");
        }
    }

    /** Used by unit tests, which pass a RestClient wired to a fake server. */
    TwilioSmsGateway(RestClient client, String accountSid, String fromNumber, String defaultCountryCode,
                     ObjectMapper objectMapper) {
        this.client = client;
        this.accountSid = accountSid;
        this.fromNumber = fromNumber;
        this.defaultCountryCode = defaultCountryCode;
        this.objectMapper = objectMapper;
    }

    @Override
    public SmsSendResult send(String toPhone, String text) {
        if (isBlank(toPhone)) {
            return SmsSendResult.failed(name(), "No destination phone number");
        }
        String to = toE164(toPhone, defaultCountryCode);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", to);
        form.add("From", fromNumber);
        form.add("Body", text);

        try {
            String json = client.post()
                    .uri("/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            String sid = objectMapper.readTree(json).path("sid").asText(null);
            log.info("Twilio accepted SMS to {} (sid {})", mask(to), sid);
            return SmsSendResult.sent(name(), sid);
        } catch (RestClientResponseException e) {
            String reason = twilioError(e.getResponseBodyAsString(), e.getStatusCode().value());
            log.warn("Twilio rejected SMS to {}: {}", mask(to), reason);
            return SmsSendResult.failed(name(), reason);
        } catch (Exception e) {
            log.warn("Could not reach Twilio for SMS to {}: {}", mask(to), e.getMessage());
            return SmsSendResult.failed(name(), "Could not reach Twilio: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "TwilioSmsGateway";
    }

    /** "9845011223" -> "+919845011223". Twilio only accepts E.164 numbers (+, country code, digits). */
    static String toE164(String phone, String defaultCountryCode) {
        String p = phone.strip().replaceAll("[\\s\\-()]", "");
        if (p.startsWith("+")) {
            return p;
        }
        if (p.startsWith("00")) {
            return "+" + p.substring(2);
        }
        String cc = defaultCountryCode.startsWith("+") ? defaultCountryCode : "+" + defaultCountryCode;
        return cc + p.replaceFirst("^0+", "");
    }

    private String twilioError(String body, int httpStatus) {
        try {
            JsonNode n = objectMapper.readTree(body);
            if (n.hasNonNull("message")) {
                return "Twilio error " + n.path("code").asText("?") + ": " + n.get("message").asText();
            }
        } catch (Exception ignored) {
            // body was not JSON
        }
        return "Twilio returned HTTP " + httpStatus;
    }

    private static RestClient buildClient(String accountSid, String authToken) {
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeaders(h -> h.setBasicAuth(accountSid == null ? "" : accountSid, authToken == null ? "" : authToken))
                .build();
    }

    private static String mask(String phone) {
        return phone.length() <= 4 ? "****" : "******" + phone.substring(phone.length() - 4);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
