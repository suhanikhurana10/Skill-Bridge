package com.kaayakasetu.auth;

import com.kaayakasetu.common.Db;
import com.kaayakasetu.common.Rs;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** All SQL for the users table. */
@Repository
public class UserRepository {

    private static final RowMapper<User> MAPPER = (rs, i) -> new User(
            rs.getLong("id"),
            rs.getString("phone"),
            rs.getString("password_hash"),
            rs.getString("name"),
            Role.valueOf(rs.getString("user_role")),
            Rs.lng(rs, "worker_id"),
            rs.getString("created_at"));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String phone, String passwordHash, String name, Role role) {
        return Db.insert(jdbc,
                "INSERT INTO users (phone, password_hash, name, user_role) VALUES (?, ?, ?, ?)",
                phone, passwordHash, name, role.name());
    }

    public Optional<User> findById(long id) {
        return first(jdbc.query("SELECT * FROM users WHERE id = ?", MAPPER, id));
    }

    public Optional<User> findByPhone(String phone) {
        return first(jdbc.query("SELECT * FROM users WHERE phone = ?", MAPPER, phone));
    }

    /** Connects a WORKER account to the worker profile it just created. */
    public void linkWorker(long userId, long workerId) {
        jdbc.update("UPDATE users SET worker_id = ? WHERE id = ?", workerId, userId);
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
