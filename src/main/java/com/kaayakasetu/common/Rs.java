package com.kaayakasetu.common;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Null-safe ResultSet readers (JDBC getDouble/getInt return 0 for SQL NULL, which would hide missing data). */
public final class Rs {
    private Rs() {}

    public static Double dbl(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }

    public static Integer integer(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    public static Long lng(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }
}
