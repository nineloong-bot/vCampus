package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.common.course.EnrollmentView;
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
}
