package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.paging.PageResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Narrow asynchronous seam used by course Swing pages. */
public interface CourseUiGateway {
    CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query);
    CompletableFuture<List<EnrollmentView>> currentEnrollments();
    CompletableFuture<List<ScheduleItem>> currentSchedule();

    static CourseUiGateway preview() {
        List<ScheduleItem> schedule = List.of(
                new ScheduleItem("s1", "o1", "MATH101", "高等数学", "01班", "张老师", "MONDAY", 1, 2, 1, 16, "教一-201"),
                new ScheduleItem("s2", "o2", "CS201", "数据结构", "02班", "李老师", "WEDNESDAY", 3, 4, 1, 16, "计算中心-305"),
                new ScheduleItem("s3", "o3", "PHYS101", "大学物理", "01班", "王老师", "FRIDAY", 5, 6, 1, 16, "教三-108"));
        List<OfferingSummary> offerings = schedule.stream().map(item -> new OfferingSummary(
                item.offeringId(), "2026-autumn", item.offeringId(), item.courseCode(), item.courseName(),
                item.teacherUserId(), item.className(), 40, 28, "OPEN", 0, List.of(item))).toList();
        return new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                return CompletableFuture.completedFuture(new PageResult<>(offerings, 0, 20, offerings.size()));
            }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of());
            }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() {
                return CompletableFuture.completedFuture(schedule);
            }
        };
    }
}
