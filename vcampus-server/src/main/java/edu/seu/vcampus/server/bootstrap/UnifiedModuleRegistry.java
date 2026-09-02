package edu.seu.vcampus.server.bootstrap;

import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.library.handler.LibraryHandlers;
import edu.seu.vcampus.server.library.repository.AccessBookRepository;
import edu.seu.vcampus.server.library.repository.AccessLibraryPolicyRepository;
import edu.seu.vcampus.server.library.repository.AccessLoanRepository;
import edu.seu.vcampus.server.library.service.LibraryAuthorizationAdapter;
import edu.seu.vcampus.server.library.service.LibraryService;
import edu.seu.vcampus.server.library.service.LibraryServiceImpl;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.shop.adapter.FoundationShopUserAdapter;
import edu.seu.vcampus.server.shop.handler.AdminShopHandlers;
import edu.seu.vcampus.server.shop.handler.BuyerShopHandlers;
import edu.seu.vcampus.server.shop.handler.SellerShopHandlers;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.payment.SimulatedPaymentService;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.service.AdminProductService;
import edu.seu.vcampus.server.shop.service.BuyerOrderService;
import edu.seu.vcampus.server.shop.service.CartService;
import edu.seu.vcampus.server.shop.service.CheckoutService;
import edu.seu.vcampus.server.shop.service.ProductService;
import edu.seu.vcampus.server.shop.service.SellerApplicationService;
import edu.seu.vcampus.server.shop.service.SellerOrderService;
import edu.seu.vcampus.server.shop.service.SellerService;
import edu.seu.vcampus.server.shop.service.ShopAdminService;
import edu.seu.vcampus.server.shop.service.ShopService;
import edu.seu.vcampus.server.student.handler.DeduplicatingStudentWriteExecutor;
import edu.seu.vcampus.server.student.handler.StudentAuthorizationPort;
import edu.seu.vcampus.server.student.handler.StudentHandlers;
import edu.seu.vcampus.server.student.handler.StudentPrincipal;
import edu.seu.vcampus.server.student.numbering.AccessCampusCardNumberGenerator;
import edu.seu.vcampus.server.student.numbering.AccessStudentNumberGenerator;
import edu.seu.vcampus.server.student.pdf.StudentProfilePdfService;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.NumberSequenceRepository;
import edu.seu.vcampus.server.student.repository.OrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentProfileApplicationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.service.StudentAdmissionCoordinator;
import edu.seu.vcampus.server.student.service.StudentOrganizationAdminService;
import edu.seu.vcampus.server.student.service.StudentProfileServiceImpl;
import edu.seu.vcampus.server.student.service.StudentQueryPort;
import edu.seu.vcampus.server.student.service.StudentServiceImpl;
import edu.seu.vcampus.server.user.repository.AccessAuditRepository;
import edu.seu.vcampus.server.user.repository.AccessUserRepository;
import edu.seu.vcampus.server.user.service.PasswordHasher;
import edu.seu.vcampus.server.user.service.UserAccountProvisioningPort;
import edu.seu.vcampus.server.user.service.UserAccountProvisioningService;
import edu.seu.vcampus.server.user.service.UserQueryPort;
import edu.seu.vcampus.server.user.service.UserServiceImpl;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/** Registers all non-course business modules against the shared application foundation. */
final class UnifiedModuleRegistry {
    private static final String SYSTEM_OPERATOR_USER_ID =
            "00000000-0000-0000-0000-000000000001";

    private UnifiedModuleRegistry() {
    }

    static void registerLibraryAndShop(MessageRouter router, TransactionManager transactions,
                                       ResourceLockManager locks,
                                       SessionRegistry sessions, AuthorizationService authorization,
                                       RequestDeduplicator deduplicator, Clock clock) {
        registerLibrary(router, transactions, locks, authorization, deduplicator, clock);
        registerShop(router, transactions, locks, sessions, authorization, deduplicator, clock);
    }

    static StudentQueryPort registerStudent(MessageRouter router, TransactionManager transactions,
                                            ResourceLockManager locks, SessionRegistry sessions,
                                            RequestDeduplicator deduplicator, UserQueryPort users,
                                            AccessUserRepository userRepository,
                                            AccessAuditRepository audits, PasswordHasher passwords) {
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
        StudentAuthorizationPort studentAuthorization = token -> {
            SessionRegistry.SessionSnapshot snapshot;
            try {
                snapshot = sessions.requireSnapshot(token);
            } catch (SessionExpiredException error) {
                throw new IllegalArgumentException("Invalid session", error);
            }
            if (snapshot.restricted()) throw new IllegalArgumentException("Invalid session");
            UserIdentity identity = snapshot.identity();
            return new StudentPrincipal(identity.userId(), Set.of(identity.role().name()),
                    snapshot.permissions());
        };
        new StudentHandlers(admissions, service,
                new StudentOrganizationAdminService(transactions, locks, organizations),
                studentAuthorization, new DeduplicatingStudentWriteExecutor(deduplicator),
                profiles, new StudentProfilePdfService()).register(router);
        return service;
    }

    private static void registerLibrary(MessageRouter router, TransactionManager transactions,
                                        ResourceLockManager locks,
                                        AuthorizationService authorization,
                                        RequestDeduplicator deduplicator, Clock clock) {
        LibraryAuthorizationAdapter libraryAuthorization =
                new LibraryAuthorizationAdapter(authorization);
        LibraryService library = new LibraryServiceImpl(libraryAuthorization,
                new AccessBookRepository(), new AccessLoanRepository(),
                new AccessLibraryPolicyRepository(), transactions, locks, clock,
                () -> UUID.randomUUID().toString());
        LibraryHandlers.register(router, library, libraryAuthorization, deduplicator);
    }

    private static void registerShop(MessageRouter router, TransactionManager transactions,
                                     ResourceLockManager locks, SessionRegistry sessions,
                                     AuthorizationService authorization,
                                     RequestDeduplicator deduplicator, Clock clock) {
        FoundationShopUserAdapter shopUsers = new FoundationShopUserAdapter(
                authorization, token -> sessions.requireSnapshot(token).restricted());
        AccessShopRepository repository = new AccessShopRepository();
        ShopBusinessLogger businessLogger = new ShopBusinessLogger();
        new BuyerShopHandlers(router, shopUsers, deduplicator,
                new ShopService(repository, transactions),
                new CartService(repository, shopUsers, transactions, locks, clock),
                new CheckoutService(repository, shopUsers, transactions, locks, clock),
                new BuyerOrderService(repository, transactions),
                new SimulatedPaymentService(shopUsers, transactions, locks, clock),
                businessLogger);
        new SellerShopHandlers(router, shopUsers, deduplicator,
                new SellerApplicationService(repository, shopUsers, transactions, locks, clock),
                new SellerService(repository, shopUsers, transactions),
                new ProductService(repository, shopUsers, transactions, locks, clock),
                new SellerOrderService(repository, shopUsers, transactions), businessLogger);
        new AdminShopHandlers(router, shopUsers, deduplicator,
                new ShopAdminService(repository, shopUsers, transactions, locks, clock),
                new AdminProductService(repository, shopUsers, transactions, locks, clock,
                        businessLogger), businessLogger);
    }
}
