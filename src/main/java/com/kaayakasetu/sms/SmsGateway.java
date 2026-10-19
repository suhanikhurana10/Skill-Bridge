package com.kaayakasetu.sms;

/**
 * The "contract" for sending an SMS. The rest of the application (ContactService) depends only on this interface,
 * never on a concrete class, so the provider can be swapped by changing ONE property (sms.provider) - no code change.
 * Implementations: {@link MockSmsGateway} (default, no keys needed) and {@link TwilioSmsGateway} (real SMS).
 */
public interface SmsGateway {

    /** Sends one text message. Must not throw for ordinary delivery problems; report them in the result instead. */
    SmsSendResult send(String toPhone, String text);

    /** Short name shown in API responses and logs, e.g. "MockSmsGateway". */
    String name();
}
