package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.*;

import java.util.List;

/** Training plan management operations. */
public interface TrainingPlanService {
    TrainingPlanDetailView getPlan(String planId);
    TrainingPlanDetailView getPlanByMajorAndYear(String majorId, int enrollmentYear);
    PageResult<TrainingPlanSummary> searchPlans(TrainingPlanQuery query);
    TrainingPlanDetailView savePlan(SaveTrainingPlanCommand command, String operatorUserId);
    TrainingPlanCourseView saveCourse(SaveTrainingPlanCourseCommand command, String operatorUserId);
    void removeCourse(String planCourseId, String operatorUserId);
    List<TrainingPlanCourseView> importCourses(ImportTrainingPlanCoursesCommand command, String operatorUserId);
    TrainingPlanDetailView getMyPlan(String userId);
}
