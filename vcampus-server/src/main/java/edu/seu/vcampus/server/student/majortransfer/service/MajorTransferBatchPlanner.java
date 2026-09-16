package edu.seu.vcampus.server.student.majortransfer.service;

import edu.seu.vcampus.server.student.domain.StudentClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure deterministic planner for balanced target-class assignment. */
public final class MajorTransferBatchPlanner {
    /** Minimal candidate facts required for assignment. */
    public record Candidate(String applicationId, String studentId, String optionId,
                            String targetDepartmentId, String targetMajorId,
                            String targetMajorCode, int cohortYear) {
        /** Validates planner input and normalizes the major code. */
        public Candidate {
            requireText(applicationId, "applicationId");
            requireText(studentId, "studentId");
            requireText(optionId, "optionId");
            requireText(targetDepartmentId, "targetDepartmentId");
            requireText(targetMajorId, "targetMajorId");
            targetMajorCode = targetMajorCode == null ? null
                    : targetMajorCode.toUpperCase(Locale.ROOT);
            if (targetMajorCode == null || !targetMajorCode.matches("[0-9A-Z]{3}")) {
                throw new IllegalArgumentException("targetMajorCode must contain three characters");
            }
            if (cohortYear < 2000 || cohortYear > 2099) {
                throw new IllegalArgumentException("cohortYear must be between 2000 and 2099");
            }
        }
    }

    /** An active target class together with its current student count. */
    public record ClassSlot(StudentClass studentClass, int currentStudents) {
        /** Validates a class slot. */
        public ClassSlot {
            if (studentClass == null || !studentClass.active()) {
                throw new IllegalArgumentException("studentClass must be active");
            }
            if (currentStudents < 0) {
                throw new IllegalArgumentException("currentStudents must not be negative");
            }
        }
    }

    /** One planned student assignment and its locked numbering sequence. */
    public record Assignment(String applicationId, String studentId, String optionId,
                             String targetDepartmentId, String targetMajorId,
                             String targetMajorCode, int cohortYear,
                             StudentClass targetClass, String sequenceKey) { }

    /**
     * Assigns candidates by application ID to the least populated matching class.
     * Ties are resolved by class number and then class ID.
     */
    public List<Assignment> plan(List<Candidate> candidates, List<ClassSlot> classSlots) {
        List<Candidate> ordered = new ArrayList<>(List.copyOf(candidates));
        List<ClassSlot> slots = List.copyOf(classSlots);
        validateCandidates(ordered);
        ordered.sort(Comparator.comparing(Candidate::applicationId));
        Map<String, Integer> projected = new HashMap<>();
        slots.forEach(slot -> projected.put(slot.studentClass().classId(), slot.currentStudents()));
        List<Assignment> assignments = new ArrayList<>();
        for (Candidate candidate : ordered) {
            StudentClass target = slots.stream()
                    .map(ClassSlot::studentClass)
                    .filter(value -> value.majorId().equals(candidate.targetMajorId()))
                    .filter(value -> value.enrollmentYear() == candidate.cohortYear())
                    .min(Comparator.comparingInt((StudentClass value) -> projected.get(value.classId()))
                            .thenComparingInt(StudentClass::classNumber)
                            .thenComparing(StudentClass::classId))
                    .orElseThrow(() -> missingClass(candidate));
            projected.compute(target.classId(), (key, count) -> count + 1);
            assignments.add(assignment(candidate, target));
        }
        return List.copyOf(assignments);
    }

    private void validateCandidates(List<Candidate> candidates) {
        Set<String> departments = new HashSet<>();
        Set<String> applications = new HashSet<>();
        Set<String> students = new HashSet<>();
        for (Candidate candidate : candidates) {
            departments.add(candidate.targetDepartmentId());
            if (!applications.add(candidate.applicationId()) || !students.add(candidate.studentId())) {
                throw new MajorTransferException("DUPLICATE_BATCH_CANDIDATE",
                        "批次中存在重复申请或学生");
            }
        }
        if (departments.size() > 1) {
            throw new MajorTransferException("MIXED_TARGET_DEPARTMENT",
                    "同一批次的目标学院必须一致");
        }
    }

    private Assignment assignment(Candidate candidate, StudentClass target) {
        String key = "STUDENT_NUMBER:" + candidate.targetMajorCode() + ":"
                + String.format("%02d", candidate.cohortYear() % 100) + ":"
                + target.classNumber();
        return new Assignment(candidate.applicationId(), candidate.studentId(),
                candidate.optionId(), candidate.targetDepartmentId(), candidate.targetMajorId(),
                candidate.targetMajorCode(), candidate.cohortYear(), target, key);
    }

    private MajorTransferException missingClass(Candidate candidate) {
        return new MajorTransferException("TARGET_CLASS_NOT_FOUND",
                "目标专业缺少 " + candidate.cohortYear() + " 级启用班级");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
