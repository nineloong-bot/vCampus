package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.BatchImportCommand;
import edu.seu.vcampus.common.student.BatchImportResult;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.CreateStudentManualCommand;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.server.routing.RequestContext;

/** Defines the student admission service contract. */
public interface StudentAdmissionService {
    /**
     * Performs the admit operation.
     * @param command the command
     * @param request the request
     * @return the operation result
     */
    StudentAdmissionResult admit(CreateStudentAdmissionCommand command, RequestContext request);

    /**
     * Performs the admit operation.
     * @param command the command
     * @param request the request
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default StudentAdmissionResult admit(CreateStudentAdmissionCommand command,
            RequestContext request, String trustedDepartmentId) {
        return admit(command, request);
    }

    /**
     * Performs the create manual operation.
     * @param command the command
     * @param request the request
     * @return the operation result
     */
    default StudentAdmissionResult createManual(CreateStudentManualCommand command, RequestContext request) {
        throw new UnsupportedOperationException("Manual student creation is not configured");
    }

    /**
     * Performs the create manual operation.
     * @param command the command
     * @param request the request
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default StudentAdmissionResult createManual(CreateStudentManualCommand command,
            RequestContext request, String trustedDepartmentId) {
        return createManual(command, request);
    }

    /**
     * Performs the batch import operation.
     * @param command the command
     * @param request the request
     * @return the operation result
     */
    default BatchImportResult batchImport(BatchImportCommand command, RequestContext request) {
        throw new UnsupportedOperationException("Batch import is not configured");
    }

    /**
     * Performs the batch import operation.
     * @param command the command
     * @param request the request
     * @param trustedDepartmentId the trusted department identifier
     * @return the operation result
     */
    default BatchImportResult batchImport(BatchImportCommand command, RequestContext request,
            String trustedDepartmentId) {
        return batchImport(command, request);
    }
}
