package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;
import edu.seu.vcampus.common.course.RetakeCommand;
import edu.seu.vcampus.common.course.RetakeEligibility;
import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.paging.PageResult;
import java.util.List;

/** Application operations owned by the course module. */
public interface CourseService {
    /**
     * Performs the list terms operation.
     * @return the operation result
     */
    java.util.List<TermView> listTerms();
    /**
     * Performs the get current term operation.
     * @return the operation result
     */
    TermView getCurrentTerm();
    /**
     * Performs the create term operation.
     * @param command the command
     * @return the operation result
     */
    TermView createTerm(CreateTermCommand command);
    /**
     * Performs the update term operation.
     * @param command the command
     * @return the operation result
     */
    TermView updateTerm(UpdateTermCommand command);
    default List<SelectionPhaseView> listSelectionPhases() { throw new UnsupportedOperationException(); }
    default SelectionPhaseView createSelectionPhase(CreateSelectionPhaseCommand command) { throw new UnsupportedOperationException(); }
    default SelectionPhaseView updateSelectionPhase(UpdateSelectionPhaseCommand command) { throw new UnsupportedOperationException(); }
    default SelectionPhaseView changeSelectionPhaseStatus(ChangeSelectionPhaseStatusCommand command) { throw new UnsupportedOperationException(); }
    /**
     * Performs the search catalog operation.
     * @param query the query
     * @return the operation result
     */
    PageResult<CourseView> searchCatalog(CourseCatalogQuery query);
    /**
     * Performs the search adjustment audits operation.
     * @param query the query
     * @return the operation result
     */
    PageResult<AdjustmentAuditView> searchAdjustmentAudits(AdjustmentAuditQuery query);
    /**
     * Performs the get term phase operation.
     * @param termId the term identifier
     * @return the operation result
     */
    TermPhaseView getTermPhase(String termId);
    /**
     * Performs the create course operation.
     * @param command the command
     * @return the operation result
     */
    CourseView createCourse(CreateCourseCommand command);
    /**
     * Performs the update course operation.
     * @param command the command
     * @return the operation result
     */
    CourseView updateCourse(UpdateCourseCommand command);
    /**
     * Performs the create offering operation.
     * @param command the command
     * @return the operation result
     */
    OfferingView createOffering(CreateOfferingCommand command);
    /**
     * Performs the update offering operation.
     * @param command the command
     * @return the operation result
     */
    OfferingView updateOffering(UpdateOfferingCommand command);
    /** Places one eligible retake student into an offering as an administrator exception. */
    default EnrollmentView adminEnrollStudent(AdminEnrollStudentCommand command) {
        throw new UnsupportedOperationException();
    }
    /**
     * Performs the search offerings operation.
     * @param query the query
     * @return the operation result
     */
    PageResult<OfferingSummary> searchOfferings(OfferingSearchQuery query);
    default StudentSelectionContextView getStudentSelectionContext(String sessionToken) { throw new UnsupportedOperationException(); }
    default PageResult<CourseSelectionView> searchStudentCourses(String sessionToken, CourseSelectionQuery query) { throw new UnsupportedOperationException(); }
    /** Enrolls the authenticated student in an offering during the normal window. */
    EnrollmentView enroll(String sessionToken, EnrollCommand command);

    /** Adds the authenticated student during the adjustment window. */
    EnrollmentView addDuringAdjustment(String sessionToken, LateAddCommand command);

    /** Drops the authenticated student's active enrollment during either mutation window. */
    void drop(String sessionToken, DropCommand command);

    /** Compatibility delegate for the former adjustment-only service contract. */
    @Deprecated
    default void dropDuringAdjustment(String sessionToken, DropCommand command) {
        drop(sessionToken, command);
    }

    /** Atomically changes the authenticated student's active enrollment to another offering. */
    EnrollmentView changeDuringAdjustment(String sessionToken, ChangeOfferingCommand command);

    /**
     * Performs the check retake eligibility operation.
     * @param sessionToken the session token
     * @param courseId the course identifier
     * @return the operation result
     */
    RetakeEligibility checkRetakeEligibility(String sessionToken, String courseId);

    /**
     * Performs the enroll retake operation.
     * @param sessionToken the session token
     * @param command the command
     * @return the operation result
     */
    EnrollmentView enrollRetake(String sessionToken, RetakeCommand command);
    /**
     * Performs the get current schedule operation.
     * @param sessionToken the session token
     * @return the operation result
     */
    List<ScheduleItem> getCurrentSchedule(String sessionToken);
    /**
     * Performs the get current enrollments operation.
     * @param sessionToken the session token
     * @return the operation result
     */
    List<EnrollmentView> getCurrentEnrollments(String sessionToken);

    /** Authorization is enforced by the Task 6 administrator message handler. */
    void importCourseOutcomes(ImportCourseOutcomesCommand command);
}
