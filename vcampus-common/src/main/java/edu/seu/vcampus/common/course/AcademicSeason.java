package edu.seu.vcampus.common.course;

import java.io.Serializable;

/** Academic season mapped to the three term columns used by the curriculum plan. */
public enum AcademicSeason implements Serializable {
    SUMMER(1, "暑期"), AUTUMN(2, "秋季"), SPRING(3, "春季");

    private final int curriculumTermOrdinal;
    private final String displayName;

    AcademicSeason(int curriculumTermOrdinal, String displayName) {
        this.curriculumTermOrdinal = curriculumTermOrdinal;
        this.displayName = displayName;
    }

    public int curriculumTermOrdinal() { return curriculumTermOrdinal; }
    public String displayName() { return displayName; }

    public static AcademicSeason fromStartMonth(int month) {
        if (month >= 7 && month <= 8) return SUMMER;
        if (month >= 9) return AUTUMN;
        return SPRING;
    }
}
