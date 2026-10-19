package com.kaayakasetu.telecom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * USSD is stateless on the network side (each key press is a separate HTTP call), so the server must
 * remember where each caller is. We store that in the telecom_sessions table, keyed by session_id.
 */
@Repository
public class TelecomSessionRepository {

    private static final TypeReference<LinkedHashMap<String, Object>> DRAFT_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TelecomSessionRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<TelecomSession> find(String sessionId) {
        List<TelecomSession> rows = jdbc.query("SELECT * FROM telecom_sessions WHERE session_id = ?",
                (rs, i) -> new TelecomSession(
                        rs.getString("session_id"),
                        rs.getString("phone_number"),
                        rs.getString("session_type"),
                        rs.getString("current_step"),
                        rs.getString("language"),
                        readDraft(rs.getString("draft_json"))),
                sessionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public TelecomSession create(String sessionId, String phone, String sessionType) {
        jdbc.update("""
                INSERT INTO telecom_sessions (session_id, phone_number, session_type, current_step, language, draft_json)
                VALUES (?, ?, ?, 'welcome', 'kannada', '{}')""", sessionId, phone, sessionType);
        return new TelecomSession(sessionId, phone, sessionType, "welcome", "kannada", new LinkedHashMap<>());
    }

    public void update(String sessionId, String currentStep, String language, Map<String, Object> draft) {
        jdbc.update("""
                UPDATE telecom_sessions
                SET current_step = ?, language = ?, draft_json = ?, updated_at = CURRENT_TIMESTAMP
                WHERE session_id = ?""", currentStep, language, writeDraft(draft), sessionId);
    }

    public void delete(String sessionId) {
        jdbc.update("DELETE FROM telecom_sessions WHERE session_id = ?", sessionId);
    }

    private Map<String, Object> readDraft(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new LinkedHashMap<>();
            }
            return objectMapper.readValue(json, DRAFT_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt USSD session draft", e);
        }
    }

    private String writeDraft(Map<String, Object> draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot save USSD session draft", e);
        }
    }
}
