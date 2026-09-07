package edu.seu.vcampus.common.library;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.assertj.core.api.Assertions.*;

class PenaltyPolicyTest {
    private final PenaltyPolicy policy = PenaltyPolicy.defaults();
    private final Instant due = Instant.parse("2026-08-01T08:00:00Z");

    @Test void chargesOnlyTheDaysWithinEachTierAndRoundsPartialDaysUp() {
        assertThat(policy.overdueFine(due, due.minusSeconds(1))).isEqualByComparingTo("0");
        assertThat(policy.overdueFine(due, due)).isEqualByComparingTo("0");
        assertThat(policy.overdueFine(due, due.plusNanos(1))).isEqualByComparingTo("0.50");
        assertThat(policy.overdueFine(due, due.plus(7, ChronoUnit.DAYS))).isEqualByComparingTo("3.50");
        assertThat(policy.overdueFine(due, due.plus(7, ChronoUnit.DAYS).plusSeconds(1))).isEqualByComparingTo("4.50");
        assertThat(policy.overdueFine(due, due.plus(30, ChronoUnit.DAYS))).isEqualByComparingTo("26.50");
        assertThat(policy.overdueFine(due, due.plus(31, ChronoUnit.DAYS))).isEqualByComparingTo("28.50");
    }

    @Test void distinguishesDamageAndLoss() {
        assertThat(policy.damageFine(ReturnCondition.NORMAL)).isEqualByComparingTo("0");
        assertThat(policy.damageFine(ReturnCondition.MINOR_DAMAGE)).isEqualByComparingTo("10");
        assertThat(policy.damageFine(ReturnCondition.MAJOR_DAMAGE)).isEqualByComparingTo("50");
        assertThat(policy.damageFine(ReturnCondition.LOST)).isEqualByComparingTo("100");
    }

    @Test void rejectsInvalidTiersNegativeAmountsAndFractionalCents() {
        assertThatThrownBy(() -> custom(7, 7, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> custom(0, 30, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> custom(7, 30, "-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> custom(7, 30, "0.001")).isInstanceOf(ArithmeticException.class);
    }

    @Test void zeroRatesDisableFines() {
        assertThat(custom(1, 2, "0").overdueFine(due, due.plus(300, ChronoUnit.DAYS)))
                .isEqualByComparingTo("0");
    }

    private PenaltyPolicy custom(int first, int second, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new PenaltyPolicy(first, second, value, value, value, value, value, value);
    }
}
