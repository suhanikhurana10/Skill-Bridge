package com.kaayakasetu.sms;

/**
 * Outcome of one send attempt. status is "SENT" (accepted by the provider) or "FAILED".
 * providerMessageId is the provider's reference (Twilio's "SM..." id); error is set only when FAILED.
 */
public record SmsSendResult(String status, String provider, String providerMessageId, String error) {

    public static SmsSendResult sent(String provider, String providerMessageId) {
        return new SmsSendResult("SENT", provider, providerMessageId, null);
    }

    public static SmsSendResult failed(String provider, String error) {
        return new SmsSendResult("FAILED", provider, null, error);
    }
}
