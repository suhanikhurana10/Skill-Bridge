package com.kaayakasetu.telecom;

import java.util.Map;

/** One in-progress USSD conversation (a row of telecom_sessions). draft = answers collected so far. */
public record TelecomSession(
        String sessionId,
        String phoneNumber,
        String sessionType,
        String currentStep,
        String language,
        Map<String, Object> draft) {}
