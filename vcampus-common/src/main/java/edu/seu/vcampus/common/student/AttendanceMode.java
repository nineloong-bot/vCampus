package edu.seu.vcampus.common.student;

import java.util.Arrays;

/** Student-selectable attendance arrangement. */
public enum AttendanceMode {
    /** Represents day student. */ DAY_STUDENT("走读"),
    /** Represents resident. */ RESIDENT("住校"),
    /** Represents lodging. */ LODGING("借宿"),
    /** Represents other. */ OTHER("其他");

    private final String displayName;

    AttendanceMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the display name result.
     * @return the computed result
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Performs the from display name operation.
     * @param value the value
     * @return the operation result
     */
    public static AttendanceMode fromDisplayName(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.displayName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知就读方式"));
    }
}
