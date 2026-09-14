package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.config.ServerConfig;
import edu.seu.vcampus.server.governance.AccessModuleAdministrationRepository;
import edu.seu.vcampus.server.governance.ModuleAdministrationService;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.security.InitialPasswordChangeRequiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.student.handler.DeduplicatingStudentWriteExecutor;
import edu.seu.vcampus.server.student.handler.StudentAuthorizationPort;
import edu.seu.vcampus.server.student.handler.StudentHandlers;
import edu.seu.vcampus.server.student.handler.StudentPrincipal;
import edu.seu.vcampus.server.student.numbering.AccessCampusCardNumberGenerator;
import edu.seu.vcampus.server.student.numbering.AccessStudentNumberGenerator;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.repository.StudentProfileApplicationRepository;
import edu.seu.vcampus.server.student.service.StudentAdmissionCoordinator;
import edu.seu.vcampus.server.student.service.StudentOrganizationAdminService;
import edu.seu.vcampus.server.student.service.StudentServiceImpl;
import edu.seu.vcampus.server.student.service.StudentProfileServiceImpl;
import edu.seu.vcampus.server.student.pdf.StudentProfilePdfService;
import edu.seu.vcampus.server.user.handler.SecurityAuditHandler;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessPermissionRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;
import edu.seu.vcampus.server.user.service.SecurityAuditService;
import edu.seu.vcampus.server.user.service.UserAccountProvisioningPort;
import edu.seu.vcampus.server.user.service.UserAccountProvisioningService;
import edu.seu.vcampus.server.user.service.UserQueryPort;
import edu.seu.vcampus.server.user.service.UserService;
import edu.seu.vcampus.server.user.service.UserServiceImpl;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Set;

/** Preserves the legacy user and student assembly used by compatibility checks. */
final class LegacyServerComposition {
    /** Seed administrator used as the fallback operator for student change records. */
    private static final String SYSTEM_OPERATOR_USER_ID =
            "00000000-0000-0000-0000-000000000001";

    private LegacyServerComposition() { }

    /** Builds compatibility services using the same database URL and configured session timeout. */
    static ServerRuntime createRuntime(ServerConfig config) {
        ConnectionProvider connections = () -> DriverManager.getConnection(ServerMain.databaseUrl(config));
        java.time.Clock clock = java.time.Clock.systemUTC();
        TransactionManager transactions = new TransactionManager(connections);
        StripedResourceLockManager locks = new StripedResourceLockManager();
        SessionRegistry sessions = new SessionRegistry(clock,
                Duration.ofMinutes(config.sessionTimeoutMinutes()));
        AccessUserRepository userRepository = new AccessUserRepository();
        AccessAuditRepository audits = new AccessAuditRepository();
        PasswordHasher passwords = new PasswordHasher();
        UserService users = new UserServiceImpl(transactions, locks,
                userRepository, new AccessPermissionRepository(),
                audits, passwords, sessions, clock);
        AuthorizationService authorization = new AuthorizationService(sessions);
        RequestDeduplicator deduplicator = new RequestDeduplicator(transactions, locks);
        ModuleAdministrationService governance = new ModuleAdministrationService(
                transactions, locks, new AccessModuleAdministrationRepository(), audits, sessions);
        SecurityAuditHandler auditHandler = new SecurityAuditHandler(authorization,
                new SecurityAuditService(transactions, audits));
        StudentHandlers students = createStudentHandlers(transactions, locks, sessions,
                deduplicator, (UserQueryPort) users, userRepository, audits, passwords);
        return new ServerRuntime(users, authorization, deduplicator, auditHandler,
                governance, students);
    }

    private static StudentHandlers createStudentHandlers(TransactionManager transactions,
            ResourceLockManager locks, SessionRegistry sessions,
            RequestDeduplicator deduplicator, UserQueryPort users,
            AccessUserRepository userRepository, AccessAuditRepository audits,
            PasswordHasher passwords) {
        StudentRepository students = new StudentRepository();
        StudentChangeRepository changes = new StudentChangeRepository();
        OrganizationRepository organizations = new AccessOrganizationRepository();
        NumberSequenceRepository sequences = new NumberSequenceRepository();
        UserAccountProvisioningPort accounts = new UserAccountProvisioningService(locks,
                userRepository, audits, passwords);
        StudentAdmissionCoordinator admissions = new StudentAdmissionCoordinator(
                transactions, locks, deduplicator, organizations,
                new AccessCampusCardNumberGenerator(sequences),
                new AccessStudentNumberGenerator(sequences), accounts, students, changes);
        StudentServiceImpl service = new StudentServiceImpl(transactions, locks, students,
                changes, organizations, users, SYSTEM_OPERATOR_USER_ID);
        StudentProfileServiceImpl profiles = new StudentProfileServiceImpl(transactions, locks,
                students, new StudentProfileApplicationRepository(), changes, users);
        StudentAuthorizationPort authorization = token -> {
            SessionRegistry.SessionSnapshot snapshot = sessions.requireSnapshot(token);
            if (snapshot.restricted()) throw new InitialPasswordChangeRequiredException();
            UserIdentity identity = snapshot.identity();
            return new StudentPrincipal(identity.userId(), Set.of(identity.role().name()),
                    snapshot.permissions());
        };
        return new StudentHandlers(admissions, service,
                new StudentOrganizationAdminService(transactions, locks, organizations),
                authorization, new DeduplicatingStudentWriteExecutor(deduplicator), profiles,
                new StudentProfilePdfService());
    }

    /** Retains named accessors used by the legacy assembly checks. */
    record ServerRuntime(UserService users, AuthorizationService authorization,
                         RequestDeduplicator deduplicator,
                         SecurityAuditHandler auditHandler,
                         ModuleAdministrationService governance,
                         StudentHandlers students) {
    }
}
