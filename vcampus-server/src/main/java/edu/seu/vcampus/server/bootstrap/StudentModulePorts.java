package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.student.service.StudentGradeService;
import edu.seu.vcampus.server.student.service.StudentQueryPort;

/** Read-only application wiring exported by the student module composition. */
record StudentModulePorts(StudentQueryPort students, StudentGradeService grades) { }
