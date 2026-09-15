package edu.seu.vcampus.server.student.repository;

import edu.seu.vcampus.common.student.*;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;

/** Implements a focused group of StudentProfileApplicationRepository persistence operations. */
abstract class StudentProfileApplicationRepositoryOperations2 extends StudentProfileApplicationRepositoryPersistenceSupport {

    public void markApproved(Connection connection, String applicationId, String reviewerUserId,
            String reviewComment, Instant reviewedAt) {
        review(connection, applicationId, reviewerUserId, reviewComment, reviewedAt, "APPROVED");
    }

    public void markRejected(Connection connection, String applicationId, String reviewerUserId,
            String reviewComment, Instant reviewedAt) {
        review(connection, applicationId, reviewerUserId, reviewComment, reviewedAt, "REJECTED");
    }
}
