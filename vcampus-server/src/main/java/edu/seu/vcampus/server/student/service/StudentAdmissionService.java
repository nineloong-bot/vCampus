package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.BatchImportCommand;
import edu.seu.vcampus.common.student.BatchImportResult;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.CreateStudentManualCommand;
import edu.seu.vcampus.common.student.FreshmanAdmissionCommand;
import edu.seu.vcampus.common.student.FreshmanAdmissionPreview;
import edu.seu.vcampus.common.student.FreshmanAdmissionResult;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.server.routing.RequestContext;

public interface StudentAdmissionService {
    StudentAdmissionResult admit(CreateStudentAdmissionCommand command, RequestContext request);

    default StudentAdmissionResult admit(CreateStudentAdmissionCommand command,
            RequestContext request, String trustedDepartmentId) {
        return admit(command, request);
    }

    default StudentAdmissionResult createManual(CreateStudentManualCommand command, RequestContext request) {
        throw new UnsupportedOperationException("Manual student creation is not configured");
    }

    default StudentAdmissionResult createManual(CreateStudentManualCommand command,
            RequestContext request, String trustedDepartmentId) {
        return createManual(command, request);
    }

    default BatchImportResult batchImport(BatchImportCommand command, RequestContext request) {
        throw new UnsupportedOperationException("Batch import is not configured");
    }

    default BatchImportResult batchImport(BatchImportCommand command, RequestContext request,
            String trustedDepartmentId) {
        return batchImport(command, request);
    }

    /** Validates freshman CSV data and returns a read-only class plan. */
    default FreshmanAdmissionPreview previewFreshmanAdmission(FreshmanAdmissionCommand command,
            RequestContext request, String trustedDepartmentId) {
        throw new UnsupportedOperationException("Freshman admission preview is not configured");
    }

    /** Atomically admits freshmen from a fixed-format CSV after server-side revalidation. */
    default FreshmanAdmissionResult admitFreshmen(FreshmanAdmissionCommand command,
            RequestContext request, String trustedDepartmentId) {
        throw new UnsupportedOperationException("Freshman admission is not configured");
    }
}
