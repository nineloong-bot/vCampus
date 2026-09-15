package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.governance.AccessModuleAdministrationRepository;
import edu.seu.vcampus.server.governance.ModuleAdministrationService;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.student.governance.AccessStudentCollegeAdministrationRepository;
import edu.seu.vcampus.server.student.governance.StudentCollegeAdministrationHandlers;
import edu.seu.vcampus.server.student.governance.StudentCollegeAdministrationService;
import edu.seu.vcampus.server.user.handler.ModuleAdministrationHandlers;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;

/** Registers platform and student-college governance on the unified router. */
final class StudentGovernanceRegistry {
    private StudentGovernanceRegistry() {
    }

    static void register(MessageRouter router, TransactionManager transactions,
            ResourceLockManager locks, SessionRegistry sessions,
            AuthorizationService authorization, RequestDeduplicator deduplicator,
            AccessAuditRepository audits, AccessUserRepository users,
            PasswordHasher passwords) {
        ModuleAdministrationService governance = new ModuleAdministrationService(
                transactions, locks, new AccessModuleAdministrationRepository(), audits, sessions);
        new ModuleAdministrationHandlers(router, governance, authorization, deduplicator);
        StudentCollegeAdministrationService colleges =
                new StudentCollegeAdministrationService(transactions, locks,
                        new AccessStudentCollegeAdministrationRepository(), audits, sessions,
                        users, passwords);
        new StudentCollegeAdministrationHandlers(router, colleges, authorization, deduplicator);
    }
}
