package edu.seu.vcampus.common.course;

import java.io.Serializable;

/** Academic season mapped to the two terms in each curriculum year. */
public enum AcademicSeason implements Serializable {
    AUTUMN(1, "秋季"), SPRING(2, "春季");

    private final int curriculumTermOrdinal;
    private final String displayName;

    AcademicSeason(int curriculumTermOrdinal, String displayName) {
        this.curriculumTermOrdinal = curriculumTermOrdinal;
        this.displayName = displayName;
    }

    public int curriculumTermOrdinal() { return curriculumTermOrdinal; }
    public String displayName() { return displayName; }

    public static AcademicSeason fromStartMonth(int month) {
        if (month >= 9 && month <= 12) return AUTUMN;
        if (month >= 1 && month <= 6) return SPRING;
        throw new IllegalArgumentException("July and August are not teaching terms");
    }
}
