package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.BatchImportCommand;
import edu.seu.vcampus.common.student.BatchImportResult;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.CreateStudentManualCommand;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.server.routing.RequestContext;

/**
 * 新生录取建档与学籍录入业务服务接口。
 */
public interface StudentAdmissionService {
    StudentAdmissionResult admit(CreateStudentAdmissionCommand command, RequestContext request);

    default StudentAdmissionResult createManual(CreateStudentManualCommand command, RequestContext request) {
        throw new UnsupportedOperationException("Manual student creation is not configured");
    }

    default BatchImportResult batchImport(BatchImportCommand command, RequestContext request) {
        throw new UnsupportedOperationException("Batch import is not configured");
    }
}
