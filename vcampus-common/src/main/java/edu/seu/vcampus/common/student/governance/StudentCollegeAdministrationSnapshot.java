package edu.seu.vcampus.common.student.governance;

import edu.seu.vcampus.common.student.DepartmentView;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Immutable data required to govern college-administrator assignments. */
/**
 * Carries immutable student college administration snapshot data.
 * @param administrators the administrators
 * @param departments the departments
 */
public record StudentCollegeAdministrationSnapshot(
        List<StudentCollegeAdministratorView> administrators,
        List<DepartmentView> departments
) implements Serializable {
    /** Defensively copies all rows exposed to clients. */
    public StudentCollegeAdministrationSnapshot {
        administrators = List.copyOf(Objects.requireNonNull(administrators));
        departments = List.copyOf(Objects.requireNonNull(departments));
    }
}
