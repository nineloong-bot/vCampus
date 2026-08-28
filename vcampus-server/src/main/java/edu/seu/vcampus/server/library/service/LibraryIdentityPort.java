package edu.seu.vcampus.server.library.service;

/** Adapts the user module's session authorization to the library module. */
@FunctionalInterface
public interface LibraryIdentityPort {
    BorrowerIdentity requireBorrower(String sessionToken);
}
