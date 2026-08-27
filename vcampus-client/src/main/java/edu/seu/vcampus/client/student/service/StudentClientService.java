package edu.seu.vcampus.client.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Typed asynchronous facade for all ten student commands. */
public final class StudentClientService {
    private final StudentRequestClient client;
    private final Duration timeout;
    public StudentClientService(StudentRequestClient client, Duration timeout) {
        this.client = Objects.requireNonNull(client); this.timeout = Objects.requireNonNull(timeout);
    }
    public CompletableFuture<ResponseBody<StudentAdmissionResult>> admit(CreateStudentAdmissionCommand value) { return client.send("STUDENT_CREATE", value, timeout); }
    public CompletableFuture<ResponseBody<StudentView>> getCurrent() { return client.send("STUDENT_GET_CURRENT", EmptyRequest.INSTANCE, timeout); }
    public CompletableFuture<ResponseBody<StudentView>> get(String id) { return client.send("STUDENT_GET", new EntityIdRequest(id), timeout); }
    public CompletableFuture<ResponseBody<PageResult<StudentSummary>>> search(StudentSearchQuery value) { return client.send("STUDENT_SEARCH", value, timeout); }
    public CompletableFuture<ResponseBody<StudentView>> updateContact(UpdateStudentContactCommand value) { return client.send("STUDENT_UPDATE_CONTACT", value, timeout); }
    public CompletableFuture<ResponseBody<StudentView>> updateEnrollment(UpdateStudentEnrollmentCommand value) { return client.send("STUDENT_UPDATE_ENROLLMENT", value, timeout); }
    public CompletableFuture<ResponseBody<StudentView>> changeStatus(ChangeStudentStatusCommand value) { return client.send("STUDENT_CHANGE_STATUS", value, timeout); }
    public CompletableFuture<ResponseBody<ArrayList<DepartmentView>>> listDepartments(boolean activeOnly) { return client.send("STUDENT_LIST_DEPARTMENTS", new ActiveOnlyQuery(activeOnly), timeout); }
    public CompletableFuture<ResponseBody<ArrayList<MajorView>>> listMajors(String departmentId) { return client.send("STUDENT_LIST_MAJORS", new ParentIdQuery(departmentId), timeout); }
    public CompletableFuture<ResponseBody<ArrayList<ClassView>>> listClasses(String majorId) { return client.send("STUDENT_LIST_CLASSES", new ParentIdQuery(majorId), timeout); }
}
