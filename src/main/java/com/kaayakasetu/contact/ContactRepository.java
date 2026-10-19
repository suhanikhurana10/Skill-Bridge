package com.kaayakasetu.contact;

import com.kaayakasetu.common.Db;
import com.kaayakasetu.common.Rs;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ContactRepository {

    private static final RowMapper<Contact> MAPPER = (rs, rowNum) -> new Contact(
            rs.getLong("id"),
            rs.getString("sender_type"),
            rs.getLong("sender_id"),
            rs.getString("receiver_type"),
            rs.getLong("receiver_id"),
            Rs.lng(rs, "job_id"),
            rs.getString("message"),
            rs.getString("status"),
            rs.getInt("phone_shared"),
            rs.getString("created_at"));

    private final JdbcTemplate jdbc;

    public ContactRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String senderType, long senderId, String receiverType, long receiverId, Long jobId,
                       String message) {
        return Db.insert(jdbc, """
                INSERT INTO contacts (
                    sender_type, sender_id, receiver_type, receiver_id, job_id, message, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'pending')""",
                senderType, senderId, receiverType, receiverId, jobId, message);
    }

    public Optional<Contact> findById(long id) {
        List<Contact> rows = jdbc.query("SELECT * FROM contacts WHERE id = ?", MAPPER, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void updateStatus(long id, String status) {
        jdbc.update("UPDATE contacts SET status = ? WHERE id = ?", status, id);
    }

    public List<Contact> findForUser(String userType, long userId) {
        return jdbc.query("""
                SELECT * FROM contacts
                WHERE (sender_type = ? AND sender_id = ?)
                   OR (receiver_type = ? AND receiver_id = ?)
                ORDER BY created_at DESC""", MAPPER, userType, userId, userType, userId);
    }
}
