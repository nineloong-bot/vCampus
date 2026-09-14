package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.*;

import java.util.List;

/** Training plan management operations. */
public interface TrainingPlanService {
    TrainingPlanDetailView getPlan(String planId);
    default TrainingPlanDetailView getPlan(String planId, String departmentId) { return getPlan(planId); }
    TrainingPlanDetailView getPlanByMajorAndYear(String majorId, int enrollmentYear);
    PageResult<TrainingPlanSummary> searchPlans(TrainingPlanQuery query);
    default PageResult<TrainingPlanSummary> searchPlans(TrainingPlanQuery query, String departmentId) { return searchPlans(query); }
    TrainingPlanDetailView savePlan(SaveTrainingPlanCommand command, String operatorUserId);
    default TrainingPlanDetailView savePlan(SaveTrainingPlanCommand command, String operatorUserId,
            String departmentId) { return savePlan(command, operatorUserId); }
    TrainingPlanCourseView saveCourse(SaveTrainingPlanCourseCommand command, String operatorUserId);
    default TrainingPlanCourseView saveCourse(SaveTrainingPlanCourseCommand command,
            String operatorUserId, String departmentId) { return saveCourse(command, operatorUserId); }
    void removeCourse(String planCourseId, String operatorUserId);
    default void removeCourse(String planCourseId, String operatorUserId, String departmentId) {
        removeCourse(planCourseId, operatorUserId);
    }
    List<TrainingPlanCourseView> importCourses(ImportTrainingPlanCoursesCommand command, String operatorUserId);
    default List<TrainingPlanCourseView> importCourses(ImportTrainingPlanCoursesCommand command,
            String operatorUserId, String departmentId) { return importCourses(command, operatorUserId); }
    TrainingPlanDetailView getMyPlan(String userId);

    List<CoursePoolItemView> listCoursePool(CoursePoolQuery query);
    CrossCourseApplicationView submitCrossCourseApplication(SubmitCrossCourseApplicationCommand command, String operatorUserId);
    default CrossCourseApplicationView submitCrossCourseApplication(
            SubmitCrossCourseApplicationCommand command, String operatorUserId,
            String departmentId) {
        return submitCrossCourseApplication(command, operatorUserId);
    }
    List<CrossCourseApplicationView> listCrossCourseApplications(CrossCourseApplicationQuery query, String operatorUserId);
    default List<CrossCourseApplicationView> listCrossCourseApplications(
            CrossCourseApplicationQuery query, String operatorUserId, String departmentId) {
        return listCrossCourseApplications(query, operatorUserId);
    }
    CrossCourseApplicationView reviewCrossCourseApplication(ReviewCrossCourseApplicationCommand command, String operatorUserId);
    default CrossCourseApplicationView reviewCrossCourseApplication(
            ReviewCrossCourseApplicationCommand command, String operatorUserId,
            String departmentId) {
        return reviewCrossCourseApplication(command, operatorUserId);
    }
}
