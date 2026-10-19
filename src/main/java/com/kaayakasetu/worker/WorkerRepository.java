package com.kaayakasetu.worker;

import com.kaayakasetu.common.Db;
import com.kaayakasetu.common.Rs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** All SQL for the workers table. */
@Repository
public class WorkerRepository {

    private static final RowMapper<Worker> MAPPER = (rs, rowNum) -> new Worker(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("phone"),
            rs.getString("trade"),
            rs.getString("secondary_skills"),
            rs.getInt("experience_years"),
            rs.getDouble("daily_rate"),
            Rs.dbl(rs, "hourly_rate"),
            rs.getString("city"),
            rs.getString("area"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"),
            rs.getString("availability"),
            rs.getDouble("rating"),
            rs.getInt("jobs_completed"),
            rs.getString("language_pref"),
            rs.getString("source"),
            rs.getString("created_at"));

    private final JdbcTemplate jdbc;

    public WorkerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String name, String phone, String trade, String secondarySkills, int experienceYears,
                       double dailyRate, double hourlyRate, String city, String area, double latitude,
                       double longitude, String availability, String languagePref, String source) {
        return Db.insert(jdbc, """
                INSERT INTO workers (
                    name, phone, trade, secondary_skills, experience_years, daily_rate, hourly_rate,
                    city, area, latitude, longitude, availability, language_pref, source
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                name, phone, trade, secondarySkills, experienceYears, dailyRate, hourlyRate,
                city, area, latitude, longitude, availability, languagePref, source);
    }

    public Optional<Worker> findById(long id) {
        return first(jdbc.query("SELECT * FROM workers WHERE id = ?", MAPPER, id));
    }

    public Optional<Worker> findByPhone(String phone) {
        return first(jdbc.query("SELECT * FROM workers WHERE phone = ?", MAPPER, phone.strip()));
    }

    public List<Worker> search(String trade, String city, String availability, Double maxDailyRate,
                               Integer minExperience, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM workers WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (trade != null && !trade.isEmpty()) {
            sql.append(" AND LOWER(trade) LIKE LOWER(?)");
            params.add("%" + trade.strip() + "%");
        }
        if (city != null && !city.isEmpty()) {
            sql.append(" AND LOWER(city) LIKE LOWER(?)");
            params.add("%" + city.strip() + "%");
        }
        if (availability != null && !availability.isEmpty()) {
            sql.append(" AND availability = ?");
            params.add(availability.strip());
        }
        // Python used `if max_daily_rate:` - so 0 / null both mean "no filter"
        if (maxDailyRate != null && maxDailyRate != 0) {
            sql.append(" AND daily_rate <= ?");
            params.add(maxDailyRate);
        }
        if (minExperience != null && minExperience != 0) {
            sql.append(" AND experience_years >= ?");
            params.add(minExperience);
        }
        sql.append(" ORDER BY rating DESC, jobs_completed DESC, id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), MAPPER, params.toArray());
    }

    /**
     * Applies a partial update. The column names come from our own code (never from the client),
     * only the VALUES come from the request, and those are bound as parameters - so no SQL injection.
     */
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
        jdbc.update("UPDATE workers SET " + String.join(", ", assignments) + " WHERE id = ?", params.toArray());
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
