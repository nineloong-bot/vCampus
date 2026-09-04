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
    java.util.List<TermView> listTerms();
    TermView getCurrentTerm();
    TermView createTerm(CreateTermCommand command);
    TermView updateTerm(UpdateTermCommand command);
    default List<SelectionPhaseView> listSelectionPhases() { throw new UnsupportedOperationException(); }
    default SelectionPhaseView createSelectionPhase(CreateSelectionPhaseCommand command) { throw new UnsupportedOperationException(); }
    default SelectionPhaseView updateSelectionPhase(UpdateSelectionPhaseCommand command) { throw new UnsupportedOperationException(); }
    default SelectionPhaseView changeSelectionPhaseStatus(ChangeSelectionPhaseStatusCommand command) { throw new UnsupportedOperationException(); }
    PageResult<CourseView> searchCatalog(CourseCatalogQuery query);
    PageResult<AdjustmentAuditView> searchAdjustmentAudits(AdjustmentAuditQuery query);
    TermPhaseView getTermPhase(String termId);
    CourseView createCourse(CreateCourseCommand command);
    CourseView updateCourse(UpdateCourseCommand command);
    OfferingView createOffering(CreateOfferingCommand command);
    OfferingView updateOffering(UpdateOfferingCommand command);
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

    RetakeEligibility checkRetakeEligibility(String sessionToken, String courseId);

    EnrollmentView enrollRetake(String sessionToken, RetakeCommand command);
    List<ScheduleItem> getCurrentSchedule(String sessionToken);
    List<EnrollmentView> getCurrentEnrollments(String sessionToken);

    /** Authorization is enforced by the Task 6 administrator message handler. */
    void importCourseOutcomes(ImportCourseOutcomesCommand command);
}
