package edu.seu.vcampus.server.student.numbering;

import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;

import java.util.Objects;

/** Transactional Access-backed campus-card allocator. */
public final class AccessCampusCardNumberGenerator implements CampusCardNumberGenerator {
    public static final String GLOBAL_SEQUENCE_KEY = "CAMPUS_CARD_GLOBAL";
    private final NumberSequenceRepository sequences;

    public AccessCampusCardNumberGenerator(NumberSequenceRepository sequences) {
        this.sequences = Objects.requireNonNull(sequences, "sequences");
    }

    @Override
    public String next(TransactionContext transaction, StudentType studentType, int enrollmentYear) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(studentType, "studentType");
        validateYear(enrollmentYear);
        var current = sequences.require(transaction.connection(), GLOBAL_SEQUENCE_KEY);
        if (current.currentValue() >= current.maxValue()) {
            throw new StudentNumberingException("STUDENT_CAMPUS_CARD_SEQUENCE_EXHAUSTED",
                    "Campus-card sequence is exhausted");
        }
        int nextValue = sequences.advance(transaction.connection(), current).currentValue();
        return "2" + studentType.digit() + "3" + twoDigits(enrollmentYear % 100)
                + String.format("%04d", nextValue);
    }

    static void validateYear(int year) {
        if (year < 2000 || year > 2099) {
            throw new StudentNumberingException("STUDENT_ENROLLMENT_YEAR_INVALID",
                    "Enrollment year must be between 2000 and 2099");
        }
    }

    static String twoDigits(int value) {
        return String.format("%02d", value);
    }
}
