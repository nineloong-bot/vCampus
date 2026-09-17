package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.List;

/** Result of an atomically committed freshman admission batch. */
public record FreshmanAdmissionResult(int totalCreated,
                                      List<FreshmanAdmissionStudentResult> students)
        implements Serializable {
    /** Validates and makes committed results immutable. */
    public FreshmanAdmissionResult {
        students = List.copyOf(students);
        if (totalCreated != students.size()) throw new IllegalArgumentException("totalCreated must match students");
    }
}
