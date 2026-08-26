package edu.seu.vcampus.server.student.numbering;

import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;

import java.util.Locale;
import java.util.Objects;

/** Transactional Access-backed student-number allocator. */
public final class AccessStudentNumberGenerator implements StudentNumberGenerator {
    private final NumberSequenceRepository sequences;

    public AccessStudentNumberGenerator(NumberSequenceRepository sequences) {
        this.sequences = Objects.requireNonNull(sequences, "sequences");
    }

    @Override
    public String next(TransactionContext transaction, String majorCode,
                       int enrollmentYear, int classNumber) {
        Objects.requireNonNull(transaction, "transaction");
        String normalizedCode = normalizeMajorCode(majorCode);
        AccessCampusCardNumberGenerator.validateYear(enrollmentYear);
        if (classNumber < 1 || classNumber > 9) {
            throw new StudentNumberingException("STUDENT_CLASS_NUMBER_INVALID",
                    "Class number must be between 1 and 9");
        }
        String year = AccessCampusCardNumberGenerator.twoDigits(enrollmentYear % 100);
        String key = "STUDENT_NUMBER:" + normalizedCode + ":" + year + ":" + classNumber;
        var current = sequences.getOrCreate(transaction.connection(), key, 99);
        if (current.currentValue() >= current.maxValue()) {
            throw new StudentNumberingException("STUDENT_CLASS_SEQUENCE_EXHAUSTED",
                    "Class student-number sequence is exhausted");
        }
        int nextValue = sequences.advance(transaction.connection(), current).currentValue();
        return normalizedCode + year + classNumber + String.format("%02d", nextValue);
    }

    private static String normalizeMajorCode(String majorCode) {
        String normalized = majorCode == null ? null : majorCode.toUpperCase(Locale.ROOT);
        if (normalized == null || !normalized.matches("[0-9A-Z]{3}")) {
            throw new StudentNumberingException("STUDENT_MAJOR_CODE_INVALID",
                    "Major code must match ^[0-9A-Z]{3}$");
        }
        return normalized;
    }
}
