package edu.seu.vcampus.server.user.repository;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.server.user.domain.UserAccount;

import java.sql.Connection;
import java.util.Optional;

/** Persistence boundary for user accounts within an existing transaction. */
public interface UserRepository {
    /** Finds an account by its internal identifier. */
    Optional<UserAccount> findById(Connection connection, String userId);

    /** Finds an account by an already normalized login identifier. */
    Optional<UserAccount> findByNormalizedLoginId(
            Connection connection, String normalizedLoginId);

    /** Inserts an account after normalizing its login identifier. */
    void insert(Connection connection, UserAccount account);

    /** Updates an account when its current row version matches the expected value. */
    void updateWithVersion(
            Connection connection, UserAccount account, long expectedVersion);

    /** Searches accounts using filters and zero-based paging. */
    PageResult<UserAccount> search(Connection connection, UserSearchQuery query);
}
