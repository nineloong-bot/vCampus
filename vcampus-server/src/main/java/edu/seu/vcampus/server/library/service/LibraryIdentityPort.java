package edu.seu.vcampus.server.library.service;

/** Adapts the user module's session authorization to the library module. */
@FunctionalInterface
public interface LibraryIdentityPort {
    /**
     * Performs the require borrower operation.
     * @param sessionToken the session token
     * @return the operation result
     */
    BorrowerIdentity requireBorrower(String sessionToken);
}
