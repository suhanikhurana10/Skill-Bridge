package com.kaayakasetu.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Number formatting equivalent to Python's f"{x:.0f}" and f"{x:,.0f}" (always Western digits/commas). */
public final class Fmt {
    private Fmt() {}

    /** Python: f"{x:.0f}" */
    public static String f0(double value) {
        return new BigDecimal(value).setScale(0, RoundingMode.HALF_EVEN).toPlainString();
    }

    /** Python: f"{x:,.0f}"  (e.g. 1250 -> "1,250"). Locale.US so a laptop set to another locale cannot change it. */
    public static String commaF0(double value) {
        BigDecimal whole = new BigDecimal(value).setScale(0, RoundingMode.HALF_EVEN);
        return String.format(Locale.US, "%,d", whole.toBigInteger());
    }
}
