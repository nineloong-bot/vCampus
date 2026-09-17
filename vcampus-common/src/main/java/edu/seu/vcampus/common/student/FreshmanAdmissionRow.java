package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.Objects;

/** One normalized row from the fixed-format freshman admission CSV. */
public record FreshmanAdmissionRow(int lineNumber, String name, String gender,
                                   String idDocumentNumber, String departmentName,
                                   String majorName) implements Serializable {
    /** Validates immutable normalized CSV row fields. */
    public FreshmanAdmissionRow {
        if (lineNumber < 2) throw new IllegalArgumentException("lineNumber must be at least 2");
        name = required(name, "name");
        gender = required(gender, "gender");
        idDocumentNumber = required(idDocumentNumber, "idDocumentNumber");
        departmentName = required(departmentName, "departmentName");
        majorName = required(majorName, "majorName");
    }

    private static String required(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
