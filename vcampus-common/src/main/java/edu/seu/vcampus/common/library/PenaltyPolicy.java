package edu.seu.vcampus.common.library;

import java.io.Serializable;
import java.io.Serial;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Cumulative overdue tiers and fixed compensation, expressed in yuan. */
public record PenaltyPolicy(int firstTierDays, int secondTierDays,
        BigDecimal firstDailyFine, BigDecimal secondDailyFine, BigDecimal thirdDailyFine,
        BigDecimal minorDamageFine, BigDecimal majorDamageFine, BigDecimal lostFine) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public PenaltyPolicy {
        if (firstTierDays < 1 || secondTierDays <= firstTierDays || secondTierDays > 3650)
            throw new IllegalArgumentException("Overdue tier limits must increase (1–3650 days)");
        firstDailyFine = money(firstDailyFine); secondDailyFine = money(secondDailyFine);
        thirdDailyFine = money(thirdDailyFine); minorDamageFine = money(minorDamageFine);
        majorDamageFine = money(majorDamageFine); lostFine = money(lostFine);
    }

    private static BigDecimal money(BigDecimal value) {
        Objects.requireNonNull(value, "fine");
        if (value.signum() < 0 || value.compareTo(new BigDecimal("1000000")) > 0)
            throw new IllegalArgumentException("Fine must be between 0 and 1000000 yuan");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static PenaltyPolicy defaults() {
        return new PenaltyPolicy(7, 30, new BigDecimal("0.50"), new BigDecimal("1.00"),
                new BigDecimal("2.00"), new BigDecimal("10.00"), new BigDecimal("50.00"), new BigDecimal("100.00"));
    }

    public BigDecimal overdueFine(Instant dueAt, Instant resolvedAt) {
        if (!resolvedAt.isAfter(dueAt)) return new BigDecimal("0.00");
        Duration overdue = Duration.between(dueAt, resolvedAt);
        long days = overdue.toDays();
        if (!overdue.minusDays(days).isZero()) days++;
        return firstDailyFine.multiply(BigDecimal.valueOf(Math.min(days, firstTierDays)))
                .add(secondDailyFine.multiply(BigDecimal.valueOf(Math.max(0, Math.min(days, secondTierDays) - firstTierDays))))
                .add(thirdDailyFine.multiply(BigDecimal.valueOf(Math.max(0, days - secondTierDays))));
    }

    public BigDecimal damageFine(ReturnCondition condition) {
        return switch (Objects.requireNonNull(condition, "condition")) {
            case NORMAL -> new BigDecimal("0.00");
            case MINOR_DAMAGE -> minorDamageFine;
            case MAJOR_DAMAGE -> majorDamageFine;
            case LOST -> lostFine;
        };
    }
}
