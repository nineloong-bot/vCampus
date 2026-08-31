package edu.seu.vcampus.server.student.pdf;

import edu.seu.vcampus.common.student.PdfDocument;
import edu.seu.vcampus.common.student.StudentProfileData;

import java.time.Instant;

/** Generates a downloadable document from approved profile data only. */
@FunctionalInterface
public interface StudentProfilePdfGenerator {
    PdfDocument generate(StudentProfileData profile, Instant generatedAt);
}
