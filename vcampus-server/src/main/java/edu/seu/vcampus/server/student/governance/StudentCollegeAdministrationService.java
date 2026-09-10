package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.common.student.governance.AssignStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.DeactivateStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.TransferStudentCollegeAdministratorCommand;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Maintains college administrators without permitting an unmanaged active college. */
public final class StudentCollegeAdministrationService {
    private final TransactionManager transactions; private final ResourceLockManager locks;
    private final StudentCollegeAdministrationRepository repository; private final AuditRepository audits;
    private final SessionRegistry sessions;
    /** Creates the service with existing transactional infrastructure. */
    public StudentCollegeAdministrationService(TransactionManager t,ResourceLockManager l,
            StudentCollegeAdministrationRepository r,AuditRepository a,SessionRegistry s){transactions=Objects.requireNonNull(t);locks=Objects.requireNonNull(l);repository=Objects.requireNonNull(r);audits=Objects.requireNonNull(a);sessions=Objects.requireNonNull(s);}
    /** Assigns an unassigned preset college administrator. */
    public void assign(String actor,AssignStudentCollegeAdministratorCommand command){
        require(command.departmentId(),command.userId()); withLocks(List.of(key("DEPARTMENT",command.departmentId()),key("USER",command.userId())),()->transactions.inTransaction(c->{repository.requireDepartment(c,command.departmentId(),command.expectedDepartmentVersion());repository.requireAdministrator(c,command.userId());repository.requireUnassigned(c,command.userId());repository.assign(c,command.departmentId(),command.userId());audits.record(c,actor,"STUDENT_COLLEGE_ADMIN_ASSIGN","USER",command.userId(),"SUCCESS");return null;}));sessions.revokeAllForUser(command.userId());}
    /** Deactivates a college administrator while protecting the final assignment. */
    public void deactivate(String actor,DeactivateStudentCollegeAdministratorCommand command){
        require(command.departmentId(),command.userId()); withLocks(List.of(key("DEPARTMENT",command.departmentId()),key("USER",command.userId())),()->transactions.inTransaction(c->{repository.requireAssignment(c,command.departmentId(),command.userId(),command.expectedAssignmentVersion());if(repository.countActive(c,command.departmentId())<=1)throw new IllegalStateException("STUDENT_LAST_COLLEGE_ADMIN_PROTECTED");repository.deactivate(c,command.departmentId(),command.userId(),command.expectedAssignmentVersion());audits.record(c,actor,"STUDENT_COLLEGE_ADMIN_DEACTIVATE","USER",command.userId(),"SUCCESS");return null;}));sessions.revokeAllForUser(command.userId());}
    /** Atomically transfers one administrator between colleges. */
    public void transfer(String actor,TransferStudentCollegeAdministratorCommand command){
        require(command.sourceDepartmentId(),command.userId());require(command.targetDepartmentId(),command.userId());List<ResourceKey> keys=List.of(key("DEPARTMENT",command.sourceDepartmentId()),key("DEPARTMENT",command.targetDepartmentId()),key("USER",command.userId())).stream().sorted(Comparator.comparing(ResourceKey::resourceType).thenComparing(ResourceKey::resourceId)).toList();withLocks(keys,()->transactions.inTransaction(c->{repository.requireAssignment(c,command.sourceDepartmentId(),command.userId(),command.expectedAssignmentVersion());repository.requireDepartment(c,command.targetDepartmentId(),command.expectedTargetDepartmentVersion());if(repository.countActive(c,command.sourceDepartmentId())<=1)throw new IllegalStateException("STUDENT_LAST_COLLEGE_ADMIN_PROTECTED");repository.transfer(c,command.sourceDepartmentId(),command.targetDepartmentId(),command.userId(),command.expectedAssignmentVersion());audits.record(c,actor,"STUDENT_COLLEGE_ADMIN_TRANSFER","USER",command.userId(),"SUCCESS");return null;}));sessions.revokeAllForUser(command.userId());}
    private <T>T withLocks(List<ResourceKey> keys,java.util.function.Supplier<T> action){return locks.withLocks(keys,action);}private static ResourceKey key(String type,String id){return new ResourceKey(type,id);}private static void require(String department,String user){if(department==null||department.isBlank()||user==null||user.isBlank())throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");}
}
