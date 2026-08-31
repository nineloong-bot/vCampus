package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.domain.UserAccount;
import edu.seu.vcampus.server.user.repository.AuditRepository;
import edu.seu.vcampus.server.user.repository.UserRepository;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;

/** User application facade that publishes account, authentication, and query operations. */
public final class UserServiceImpl implements UserService, UserQueryPort {
    private final TransactionManager transactions;
    private final UserRepository users;
    private final TeacherAccountApplicationService applications;
    private final AuthenticationService authentication;
    private final AdminUserService administration;

    /** Creates the service with production clock and session defaults. */
    public UserServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            UserRepository users, AuditRepository audits, PasswordHasher hasher) {
        this(transactions, locks, users, audits, hasher, new SessionRegistry(), Clock.systemUTC());
    }

    /** Creates the service with injectable session and clock dependencies. */
    public UserServiceImpl(TransactionManager transactions, ResourceLockManager locks,
            UserRepository users, AuditRepository audits, PasswordHasher hasher,
            SessionRegistry sessions, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.users = Objects.requireNonNull(users, "users");
        applications = new TeacherAccountApplicationService(transactions, locks, users, audits, hasher);
        authentication = new AuthenticationService(transactions, locks, users, audits, hasher, sessions, clock);
        administration = new AdminUserService(transactions, locks, users, audits,
                authentication::revokeSessionsForUser, clock);
    }

    /** Creates a pending teacher account application. */
    @Override public UserView applyForTeacherAccount(TeacherAccountApplicationCommand command) {
        return applications.apply(command);
    }

    /** Authenticates credentials and creates a normal or restricted session. */
    @Override public LoginResult login(LoginCommand command, ClientContext context) {
        return authentication.login(command, context);
    }

    /** Revokes a session token. */
    @Override public void logout(String sessionToken) { authentication.logout(sessionToken); }

    /** Gets the current account projection. */
    @Override public UserView getCurrentUser(String sessionToken) { return authentication.currentUser(sessionToken); }

    /** Changes an account password and revokes the account's sessions. */
    @Override public void changePassword(String sessionToken, ChangePasswordCommand command) {
        authentication.changePassword(sessionToken, command);
    }

    /** Searches safe account summaries. */
    @Override public PageResult<UserSummary> searchUsers(UserSearchQuery query) { return administration.search(query); }

    /** Changes an account role. */
    @Override public UserView updateRole(UpdateUserRoleCommand command) {
        return updateRole(command.userId(), command);
    }

    @Override public UserView updateRole(String actorId, UpdateUserRoleCommand command) {
        return administration.updateRole(actorId, command);
    }

    /** Changes an account lifecycle status. */
    @Override public UserView changeStatus(ChangeUserStatusCommand command) {
        return changeStatus(command.userId(), command);
    }

    @Override public UserView changeStatus(String actorId, ChangeUserStatusCommand command) {
        return administration.changeStatus(actorId, command);
    }

    /** Revokes all sessions belonging to an account. */
    @Override public void revokeSessionsForUser(String userId) {
        authentication.revokeSessionsForUser(userId);
    }

    /** Finds an active user identity. */
    @Override public Optional<UserIdentity> findActiveUser(String userId) {
        return findByUserId(userId).filter(identity -> transactions.inTransaction(connection ->
                users.findById(connection, identity.userId())
                        .map(account -> account.accountStatus() == ACTIVE).orElse(false)));
    }

    /** Finds a user identity by its id. */
    @Override public Optional<UserIdentity> findByUserId(String userId) {
        return find(connection -> users.findById(connection, userId));
    }

    /** Finds a user identity by its login id. */
    @Override public Optional<UserIdentity> findByLoginId(String loginId) {
        String normalized = Objects.requireNonNull(loginId, "loginId").strip().toUpperCase(Locale.ROOT);
        return find(connection -> users.findByNormalizedLoginId(connection, normalized));
    }

    /** Tests a current account role. */
    @Override public boolean hasRole(String userId, UserRole role) {
        return findByUserId(userId).map(identity -> identity.role() == role).orElse(false);
    }

    private Optional<UserIdentity> find(Function<java.sql.Connection, Optional<UserAccount>> lookup) {
        return transactions.inTransaction(connection -> lookup.apply(connection).map(UserViews::identity));
    }
}
