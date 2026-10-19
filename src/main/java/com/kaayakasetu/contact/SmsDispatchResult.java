package com.kaayakasetu.contact;

/**
 * Result of a "send SMS notification" request. JSON: dispatch_status, provider, timestamp ...
 * dispatchStatus is "SENT" or "FAILED"; providerMessageId / error come from the SmsGateway that was used.
 */
public record SmsDispatchResult(
        String dispatchStatus,
        String provider,
        String timestamp,
        String toPhone,
        String senderPhone,
        String notificationText,
        String providerMessageId,
        String error) {}
