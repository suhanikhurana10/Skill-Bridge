package com.kaayakasetu.sms;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default gateway: sends nothing, just writes the message to the log. Active when sms.provider=mock
 * or when the property is not set at all (matchIfMissing), so the app starts without any API keys.
 */
@Component
@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(MockSmsGateway.class);

    @Override
    public SmsSendResult send(String toPhone, String text) {
        log.info("[MOCK SMS] to={} text={}", toPhone, text);
        return SmsSendResult.sent(name(), "mock-" + UUID.randomUUID());
    }

    @Override
    public String name() {
        return "MockSmsGateway";
    }
}
