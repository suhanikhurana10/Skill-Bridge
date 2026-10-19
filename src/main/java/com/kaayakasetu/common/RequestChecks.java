package com.kaayakasetu.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Query-parameter range checks. FastAPI did this declaratively with Query(ge=1, le=100) and answered
 * HTTP 422 when violated; we do the same explicitly so behaviour is identical on every Spring version.
 */
public final class RequestChecks {
    private RequestChecks() {}

    public static void range(String name, double value, double min, double max) {
        if (value < min || value > max) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Query parameter '" + name + "' must be between " + trim(min) + " and " + trim(max));
        }
    }

    public static void atLeast(String name, double value, double min) {
        if (value < min) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Query parameter '" + name + "' must be >= " + trim(min));
        }
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
