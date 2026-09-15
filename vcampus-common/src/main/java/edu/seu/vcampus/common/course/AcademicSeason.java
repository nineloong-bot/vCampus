package edu.seu.vcampus.common.course;

import java.io.Serializable;

/** Academic season mapped to the three term columns used by the curriculum plan. */
public enum AcademicSeason implements Serializable {
    /** Represents summer. */ SUMMER(1, "暑期"), /** Represents autumn. */ AUTUMN(2, "秋季"), /** Represents spring. */ SPRING(3, "春季");

    private final int curriculumTermOrdinal;
    private final String displayName;

    AcademicSeason(int curriculumTermOrdinal, String displayName) {
        this.curriculumTermOrdinal = curriculumTermOrdinal;
        this.displayName = displayName;
    }

    /**
 * Returns the curriculum term ordinal result.
 * @return the computed result
 */
public int curriculumTermOrdinal() { return curriculumTermOrdinal; }
    /**
 * Returns the display name result.
 * @return the computed result
 */
public String displayName() { return displayName; }

    /**
     * Performs the from start month operation.
     * @param month the month
     * @return the operation result
     */
    public static AcademicSeason fromStartMonth(int month) {
        if (month >= 7 && month <= 8) return SUMMER;
        if (month >= 9) return AUTUMN;
        return SPRING;
    }
}
