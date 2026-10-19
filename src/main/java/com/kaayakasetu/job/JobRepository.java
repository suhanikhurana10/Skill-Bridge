package com.kaayakasetu.job;

import com.kaayakasetu.common.Db;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** All SQL for the jobs table. */
@Repository
public class JobRepository {

    private static final RowMapper<Job> MAPPER = (rs, rowNum) -> new Job(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("trade"),
            rs.getString("description"),
            rs.getString("employer_name"),
            rs.getString("employer_phone"),
            rs.getString("city"),
            rs.getString("area"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"),
            rs.getInt("duration_days"),
            rs.getDouble("wage_offered"),
            rs.getString("payment_type"),
            rs.getString("urgency"),
            rs.getInt("workers_needed"),
            rs.getString("status"),
            rs.getString("created_at"));

    private final JdbcTemplate jdbc;

    public JobRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String title, String trade, String description, String employerName, String employerPhone,
                       String city, String area, double latitude, double longitude, int durationDays,
                       double wageOffered, String paymentType, String urgency, int workersNeeded, Long ownerUserId) {
        return Db.insert(jdbc, """
                INSERT INTO jobs (
                    title, trade, description, employer_name, employer_phone,
                    city, area, latitude, longitude, duration_days, wage_offered,
                    payment_type, urgency, workers_needed, status, owner_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'open', ?)""",
                title, trade, description, employerName, employerPhone,
                city, area, latitude, longitude, durationDays, wageOffered,
                paymentType, urgency, workersNeeded, ownerUserId);
    }

    /** The employer account that owns the job. Empty when the job does not exist OR has no owner (old demo jobs). */
    public Optional<Long> findOwnerUserId(long jobId) {
        List<Long> rows = jdbc.queryForList("SELECT owner_user_id FROM jobs WHERE id = ?", Long.class, jobId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    public List<Job> findByOwner(long ownerUserId) {
        return jdbc.query("SELECT * FROM jobs WHERE owner_user_id = ? ORDER BY created_at DESC, id DESC",
                MAPPER, ownerUserId);
    }

    public Optional<Job> findById(long id) {
        List<Job> rows = jdbc.query("SELECT * FROM jobs WHERE id = ?", MAPPER, id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Job> search(String trade, String city, String status, String urgency, Double minWage,
                            int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM jobs WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (trade != null && !trade.isEmpty()) {
            sql.append(" AND LOWER(trade) LIKE LOWER(?)");
            params.add("%" + trade.strip() + "%");
        }
        if (city != null && !city.isEmpty()) {
            sql.append(" AND LOWER(city) LIKE LOWER(?)");
            params.add("%" + city.strip() + "%");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status.strip());
        }
        if (urgency != null && !urgency.isEmpty()) {
            sql.append(" AND urgency = ?");
            params.add(urgency.strip());
        }
        if (minWage != null && minWage != 0) {
            sql.append(" AND wage_offered >= ?");
            params.add(minWage);
        }
        // urgent jobs first, then newest
        sql.append(" ORDER BY CASE urgency WHEN 'urgent' THEN 1 ELSE 2 END, created_at DESC, id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), MAPPER, params.toArray());
    }

    /** Column names are fixed in our code; only values come from the client (bound as parameters). */
    public void update(long id, Map<String, Object> columnValues) {
        if (columnValues.isEmpty()) {
            return;
        }
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> e : columnValues.entrySet()) {
            assignments.add(e.getKey() + " = ?");
            params.add(e.getValue());
        }
        params.add(id);
        jdbc.update("UPDATE jobs SET " + String.join(", ", assignments) + " WHERE id = ?", params.toArray());
    }
}
