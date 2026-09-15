package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** One eligibility check item shown to the student before applying. */
/**
 * Carries immutable major transfer eligibility item data.
 * @param label the label
 * @param passed the passed
 * @param detail the detail
 */
public record MajorTransferEligibilityItem(
        String label,
        boolean passed,
        String detail
) implements Serializable { }
