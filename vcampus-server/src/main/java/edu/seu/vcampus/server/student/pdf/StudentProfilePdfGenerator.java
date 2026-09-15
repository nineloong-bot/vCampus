package edu.seu.vcampus.server.student.pdf;

import edu.seu.vcampus.common.student.PdfDocument;
import edu.seu.vcampus.common.student.StudentProfileData;

import java.time.Instant;

/** Generates a downloadable document from approved profile data only. */
@FunctionalInterface
public interface StudentProfilePdfGenerator {
    /**
     * Performs the generate operation.
     * @param profile the profile
     * @param generatedAt the generated at
     * @return the operation result
     */
    PdfDocument generate(StudentProfileData profile, Instant generatedAt);
}
