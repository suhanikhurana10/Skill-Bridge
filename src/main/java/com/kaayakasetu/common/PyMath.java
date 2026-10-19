package com.kaayakasetu.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Python and Java round differently, and the wage/score maths must give identical numbers.
 *  - Python round(x) / round(x, n) rounds exact ties to the EVEN neighbour ("banker's rounding").
 *  - Java Math.round() rounds ties UP.
 * These helpers reproduce Python's behaviour so the Java port returns the same figures.
 */
public final class PyMath {
    private PyMath() {}

    /** Python: round(value, digits) */
    public static double round(double value, int digits) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        return new BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** Python: round(value) (to a whole number, ties to even) */
    public static double roundToWhole(double value) {
        return Math.rint(value);
    }

    /** Python truthiness of an optional float: None and 0.0 are both "falsy". */
    public static boolean truthy(Double value) {
        return value != null && value != 0.0;
    }
}
