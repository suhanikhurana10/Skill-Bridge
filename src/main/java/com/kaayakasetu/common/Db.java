package com.kaayakasetu.common;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/** Small JDBC helpers shared by the repositories. */
public final class Db {
    private Db() {}

    /**
     * Runs an INSERT and returns the new AUTO_INCREMENT id.
     * MySQL has no "INSERT ... RETURNING", so we ask JDBC for the generated key instead
     * (Statement.RETURN_GENERATED_KEYS). That is the portable way and works on MySQL and H2.
     */
    public static long insert(JdbcTemplate jdbc, String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            new ArgumentPreparedStatementSetter(args).setValues(ps);
            return ps;
        }, keys);

        List<Map<String, Object>> rows = keys.getKeyList();
        if (!rows.isEmpty()) {
            for (Map.Entry<String, Object> e : rows.get(0).entrySet()) {
                // MySQL names the column GENERATED_KEY, H2 names it ID
                if (("id".equalsIgnoreCase(e.getKey()) || "GENERATED_KEY".equalsIgnoreCase(e.getKey()))
                        && e.getValue() instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        throw new IllegalStateException("INSERT did not return an id");
    }
}
