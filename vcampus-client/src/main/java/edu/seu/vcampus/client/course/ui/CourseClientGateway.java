package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.RetakeCommand;
import edu.seu.vcampus.common.course.RetakeEligibility;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.paging.PageResult;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Production adapter from Swing's narrow seam to the typed course client. */
final class CourseClientGateway implements CourseUiGateway {
    private final CourseClientService client;

    CourseClientGateway(CourseClientService client) {
        this.client = Objects.requireNonNull(client);
    }

    public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
        return client.searchOfferings(query);
    }

    public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
        return client.getCurrentEnrollments();
    }

    public CompletableFuture<List<ScheduleItem>> currentSchedule() {
        return client.getCurrentSchedule();
    }

    public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) {
        return client.enroll(command);
    }

    public CompletableFuture<EnrollmentView> lateAdd(LateAddCommand command) { return client.addDuringAdjustment(command); }
    public CompletableFuture<EmptyResponse> drop(DropCommand command) { return client.dropDuringAdjustment(command); }
    public CompletableFuture<EnrollmentView> change(ChangeOfferingCommand command) { return client.changeDuringAdjustment(command); }
    public CompletableFuture<RetakeEligibility> checkRetake(String courseId) { return client.checkRetakeEligibility(courseId); }
    public CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand command) { return client.enrollRetake(command); }
}
