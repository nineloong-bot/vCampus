package edu.seu.vcampus.common.student.majortransfer;

import java.io.Serializable;

/** One eligibility check item shown to the student before applying. */
public record MajorTransferEligibilityItem(
        String label,
        boolean passed,
        String detail
) implements Serializable { }
