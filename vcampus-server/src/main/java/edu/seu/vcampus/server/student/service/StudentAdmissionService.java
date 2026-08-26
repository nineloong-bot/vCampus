package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.server.routing.RequestContext;

public interface StudentAdmissionService {
    StudentAdmissionResult admit(CreateStudentAdmissionCommand command, RequestContext request);
}
