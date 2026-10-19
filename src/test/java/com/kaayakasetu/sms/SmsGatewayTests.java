package com.kaayakasetu.sms;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The Twilio gateway is tested against a fake HTTP server, so no account, no network and no real SMS. */
class SmsGatewayTests {

    private static final String URL = "https://api.twilio.com/2010-04-01/Accounts/ACtest/Messages.json";

    private TwilioSmsGateway gatewayBoundTo(MockRestServiceServer[] holder) {
        RestClient.Builder builder = RestClient.builder().baseUrl(TwilioSmsGateway.BASE_URL);
        holder[0] = MockRestServiceServer.bindTo(builder).build();
        return new TwilioSmsGateway(builder.build(), "ACtest", "+15005550006", "+91", new ObjectMapper());
    }

    @Test
    void mockGatewayAlwaysSucceeds() {
        SmsSendResult r = new MockSmsGateway().send("9845011223", "hello");
        assertEquals("SENT", r.status());
        assertEquals("MockSmsGateway", r.provider());
        assertTrue(r.providerMessageId().startsWith("mock-"));
        assertNull(r.error());
    }

    @Test
    void phoneNumbersAreConvertedToE164() {
        assertEquals("+919845011223", TwilioSmsGateway.toE164("9845011223", "+91"));
        assertEquals("+919845011223", TwilioSmsGateway.toE164("098450 11223", "+91"));
        assertEquals("+919845011223", TwilioSmsGateway.toE164("+919845011223", "+91"));
        assertEquals("+14155550100", TwilioSmsGateway.toE164("0014155550100", "+91"));
        assertEquals("+919845011223", TwilioSmsGateway.toE164("9845011223", "91"));
    }

    @Test
    void twilioSuccessReturnsMessageSid() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TwilioSmsGateway gateway = gatewayBoundTo(server);
        server[0].expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", Matchers.containsString("application/x-www-form-urlencoded")))
                .andExpect(content().string(Matchers.containsString("To=%2B919845011223")))
                .andExpect(content().string(Matchers.containsString("From=%2B15005550006")))
                .andRespond(withSuccess("{\"sid\":\"SM123\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        SmsSendResult r = gateway.send("9845011223", "hello");

        assertEquals("SENT", r.status());
        assertEquals("TwilioSmsGateway", r.provider());
        assertEquals("SM123", r.providerMessageId());
        server[0].verify();
    }

    @Test
    void twilioRejectionBecomesFailedResultNotException() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TwilioSmsGateway gateway = gatewayBoundTo(server);
        server[0].expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":21608,\"message\":\"unverified number\"}"));

        SmsSendResult r = gateway.send("9845011223", "hello");

        assertEquals("FAILED", r.status());
        assertTrue(r.error().contains("21608"));
        assertTrue(r.error().contains("unverified number"));
    }

    @Test
    void blankDestinationFailsWithoutCallingTwilio() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TwilioSmsGateway gateway = gatewayBoundTo(server);
        assertEquals("FAILED", gateway.send("  ", "hello").status());
        server[0].verify();   // no request was expected, none was made
    }

    @Test
    void twilioGatewayRefusesToStartWithoutCredentials() {
        assertThrows(IllegalStateException.class,
                () -> new TwilioSmsGateway("", "", "", "+91", new ObjectMapper()));
    }
}
