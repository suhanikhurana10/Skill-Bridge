package com.kaayakasetu.telecom;

/** action is "CON" (keep the session open) or "END" (close it). message already starts with the action word. */
public record UssdResponse(String sessionId, String message, String action) {}
