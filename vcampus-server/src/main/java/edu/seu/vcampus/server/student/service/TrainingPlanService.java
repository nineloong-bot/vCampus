package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.*;

import java.util.List;

/** Training plan management operations. */
public interface TrainingPlanService {
    /**
     * Performs the get plan operation.
     * @param planId the plan identifier
     * @return the operation result
     */
    TrainingPlanDetailView getPlan(String planId);
    default TrainingPlanDetailView getPlan(String planId, String departmentId) { return getPlan(planId); }
    /**
     * Performs the get plan by major and year operation.
     * @param majorId the major identifier
     * @param enrollmentYear the enrollment year
     * @return the operation result
     */
    TrainingPlanDetailView getPlanByMajorAndYear(String majorId, int enrollmentYear);
    /**
     * Performs the search plans operation.
     * @param query the query
     * @return the operation result
     */
    PageResult<TrainingPlanSummary> searchPlans(TrainingPlanQuery query);
    default PageResult<TrainingPlanSummary> searchPlans(TrainingPlanQuery query, String departmentId) { return searchPlans(query); }
    /**
     * Performs the save plan operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    TrainingPlanDetailView savePlan(SaveTrainingPlanCommand command, String operatorUserId);
    default TrainingPlanDetailView savePlan(SaveTrainingPlanCommand command, String operatorUserId,
            String departmentId) { return savePlan(command, operatorUserId); }
    /**
     * Performs the save course operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    TrainingPlanCourseView saveCourse(SaveTrainingPlanCourseCommand command, String operatorUserId);
    default TrainingPlanCourseView saveCourse(SaveTrainingPlanCourseCommand command,
            String operatorUserId, String departmentId) { return saveCourse(command, operatorUserId); }
    /**
     * Performs the remove course operation.
     * @param planCourseId the plan course identifier
     * @param operatorUserId the operator user identifier
     */
    void removeCourse(String planCourseId, String operatorUserId);
    /**
     * Performs the remove course operation.
     * @param planCourseId the plan course identifier
     * @param operatorUserId the operator user identifier
     * @param departmentId the department identifier
     */
    default void removeCourse(String planCourseId, String operatorUserId, String departmentId) {
        removeCourse(planCourseId, operatorUserId);
    }
    /**
     * Performs the import courses operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    List<TrainingPlanCourseView> importCourses(ImportTrainingPlanCoursesCommand command, String operatorUserId);
    default List<TrainingPlanCourseView> importCourses(ImportTrainingPlanCoursesCommand command,
            String operatorUserId, String departmentId) { return importCourses(command, operatorUserId); }
    /**
     * Performs the get my plan operation.
     * @param userId the user identifier
     * @return the operation result
     */
    TrainingPlanDetailView getMyPlan(String userId);

    /**
     * Performs the list course pool operation.
     * @param query the query
     * @return the operation result
     */
    List<CoursePoolItemView> listCoursePool(CoursePoolQuery query);
    /**
     * Performs the submit cross course application operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    CrossCourseApplicationView submitCrossCourseApplication(SubmitCrossCourseApplicationCommand command, String operatorUserId);
    /**
     * Performs the submit cross course application operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @param departmentId the department identifier
     * @return the operation result
     */
    default CrossCourseApplicationView submitCrossCourseApplication(
            SubmitCrossCourseApplicationCommand command, String operatorUserId,
            String departmentId) {
        return submitCrossCourseApplication(command, operatorUserId);
    }
    /**
     * Performs the list cross course applications operation.
     * @param query the query
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    List<CrossCourseApplicationView> listCrossCourseApplications(CrossCourseApplicationQuery query, String operatorUserId);
    /**
     * Performs the list cross course applications operation.
     * @param query the query
     * @param operatorUserId the operator user identifier
     * @param departmentId the department identifier
     * @return the operation result
     */
    default List<CrossCourseApplicationView> listCrossCourseApplications(
            CrossCourseApplicationQuery query, String operatorUserId, String departmentId) {
        return listCrossCourseApplications(query, operatorUserId);
    }
    /**
     * Performs the review cross course application operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @return the operation result
     */
    CrossCourseApplicationView reviewCrossCourseApplication(ReviewCrossCourseApplicationCommand command, String operatorUserId);
    /**
     * Performs the review cross course application operation.
     * @param command the command
     * @param operatorUserId the operator user identifier
     * @param departmentId the department identifier
     * @return the operation result
     */
    default CrossCourseApplicationView reviewCrossCourseApplication(
            ReviewCrossCourseApplicationCommand command, String operatorUserId,
            String departmentId) {
        return reviewCrossCourseApplication(command, operatorUserId);
    }
}
