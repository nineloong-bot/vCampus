package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.BatchImportCommand;
import edu.seu.vcampus.common.student.BatchImportResult;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.CreateStudentManualCommand;
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
}
