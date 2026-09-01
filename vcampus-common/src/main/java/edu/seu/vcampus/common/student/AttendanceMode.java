package edu.seu.vcampus.common.student;

import java.util.Arrays;

/** Student-selectable attendance arrangement. */
public enum AttendanceMode {
    DAY_STUDENT("走读"),
    RESIDENT("住校"),
    LODGING("借宿"),
    OTHER("其他");

    private final String displayName;

    AttendanceMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static AttendanceMode fromDisplayName(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.displayName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知就读方式"));
    }
}
