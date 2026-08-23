package dev.coverdict.analysis.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One named percentage (schema {@code $defs/metric}, hard rule 5): every
 * number states its numerator/denominator names, never a bare percentage.
 * {@code percent} is {@code null} when {@code denominator} is 0 - never
 * reported as 0 or 100 by convention (hard rule 3a).
 */
public record Metric(String numeratorName, int numerator, String denominatorName, int denominator, BigDecimal percent) {

    static Metric of(String numeratorName, int numerator, String denominatorName, int denominator) {
        BigDecimal percent = denominator == 0
            ? null
            : BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
        return new Metric(numeratorName, numerator, denominatorName, denominator, percent);
    }
}
