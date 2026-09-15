package edu.seu.vcampus.server.library.service;

/** Prevents direct copy edits from bypassing an effective loan. */
public final class CopyHasActiveLoanException extends IllegalStateException {
    /**
     * Creates a copy has active loan exception with its required collaborators.
     * @param copyId the copy identifier
     */
    public CopyHasActiveLoanException(String copyId) {
        super("Copy has an active loan: " + copyId);
    }
}
