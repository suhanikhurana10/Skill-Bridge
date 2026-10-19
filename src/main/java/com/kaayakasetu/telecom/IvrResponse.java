package com.kaayakasetu.telecom;

import java.util.List;
import java.util.Map;

/** IVR answer: TwiML for a real telephony provider + plain text for the on-screen phone simulator. */
public record IvrResponse(
        String callSid,
        String twimlXml,
        String promptText,
        String promptAudioText,
        String language,
        String action,
        List<Map<String, String>> options) {}
