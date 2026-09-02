package edu.seu.vcampus.client.course.service;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.EntityIdRequest;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Typed, non-blocking client facade for the complete course command surface. */
public final class CourseClientService {
    private static final Duration READ = Duration.ofSeconds(10);
    private static final Duration WRITE = Duration.ofSeconds(15);
    private final CourseTransport transport;
    private final Executor executor;
    private final CopyOnWriteArrayList<Consumer<CourseClientException>> authenticationFailureListeners =
            new CopyOnWriteArrayList<>();

    public CourseClientService(ClientConnection connection) { this(connection::send, ForkJoinPool.commonPool()); }
    public CourseClientService(CourseTransport transport) { this(transport, ForkJoinPool.commonPool()); }
    public CourseClientService(CourseTransport transport, Executor executor) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** Registers a listener for failures that invalidate the logged-in course shell. */
    public Runnable addAuthenticationFailureListener(Consumer<CourseClientException> listener) {
        authenticationFailureListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> authenticationFailureListeners.remove(listener);
    }

    public CompletableFuture<List<TermView>> listTerms() { return callList("COURSE_TERM_LIST", EmptyRequest.INSTANCE, READ, TermView.class); }
    public CompletableFuture<TermView> getCurrentTerm() { return call("COURSE_GET_CURRENT_TERM", EmptyRequest.INSTANCE, READ, TermView.class); }
    public CompletableFuture<TermView> createTerm(CreateTermCommand c) { return call("COURSE_TERM_CREATE", c, WRITE, TermView.class); }
    public CompletableFuture<TermView> updateTerm(UpdateTermCommand c) { return call("COURSE_TERM_UPDATE", c, WRITE, TermView.class); }
    public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery q) { return callPage("COURSE_CATALOG_SEARCH", q, READ, CourseView.class); }
    public CompletableFuture<PageResult<AdjustmentAuditView>> searchAdjustmentAudits(AdjustmentAuditQuery q) { return callPage("COURSE_ADJUSTMENT_AUDIT_SEARCH", q, READ, AdjustmentAuditView.class); }
    public CompletableFuture<TermPhaseView> getTermPhase(String id) { return call("COURSE_GET_TERM_PHASE", new EntityIdRequest(id), READ, TermPhaseView.class); }
    public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery q) { return callPage("COURSE_SEARCH_OFFERINGS", q, READ, OfferingSummary.class); }
    public CompletableFuture<EnrollmentView> enroll(EnrollCommand c) { return call("COURSE_ENROLL", c, WRITE, EnrollmentView.class); }
    public CompletableFuture<EnrollmentView> addDuringAdjustment(LateAddCommand c) { return call("COURSE_ADJUSTMENT_ADD", c, WRITE, EnrollmentView.class); }
    public CompletableFuture<EmptyResponse> drop(DropCommand c) { return call("COURSE_DROP", c, WRITE, EmptyResponse.class); }
    /** Compatibility delegate for callers compiled against the former adjustment-only facade. */
    @Deprecated
    public CompletableFuture<EmptyResponse> dropDuringAdjustment(DropCommand c) { return drop(c); }
    public CompletableFuture<EnrollmentView> changeDuringAdjustment(ChangeOfferingCommand c) { return call("COURSE_ADJUSTMENT_CHANGE", c, WRITE, EnrollmentView.class); }
    public CompletableFuture<RetakeEligibility> checkRetakeEligibility(String id) { return call("COURSE_RETAKE_CHECK", new EntityIdRequest(id), READ, RetakeEligibility.class); }
    public CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand c) { return call("COURSE_RETAKE_ENROLL", c, WRITE, EnrollmentView.class); }
    public CompletableFuture<List<ScheduleItem>> getCurrentSchedule() { return callList("COURSE_GET_MY_SCHEDULE", EmptyRequest.INSTANCE, READ, ScheduleItem.class); }
    public CompletableFuture<List<EnrollmentView>> getCurrentEnrollments() { return callList("COURSE_GET_MY_ENROLLMENTS", EmptyRequest.INSTANCE, READ, EnrollmentView.class); }
    public CompletableFuture<EmptyResponse> importOutcomes(ImportCourseOutcomesCommand c) { return call("COURSE_IMPORT_OUTCOMES", c, WRITE, EmptyResponse.class); }
    public CompletableFuture<CourseView> createCourse(CreateCourseCommand c) { return call("COURSE_CREATE", c, WRITE, CourseView.class); }
    public CompletableFuture<CourseView> updateCourse(UpdateCourseCommand c) { return call("COURSE_UPDATE", c, WRITE, CourseView.class); }
    public CompletableFuture<OfferingView> createOffering(CreateOfferingCommand c) { return call("COURSE_CREATE_OFFERING", c, WRITE, OfferingView.class); }
    public CompletableFuture<OfferingView> updateOffering(UpdateOfferingCommand c) { return call("COURSE_UPDATE_OFFERING", c, WRITE, OfferingView.class); }

    private <T extends Serializable> CompletableFuture<T> call(String command, Serializable body, Duration timeout, Class<T> type) {
        return raw(command, body, timeout).thenApply(data -> requireType(data, type));
    }

    private <T extends Serializable> CompletableFuture<List<T>> callList(String command, Serializable body, Duration timeout, Class<T> itemType) {
        return raw(command, body, timeout).thenApply(data -> {
            if (!(data instanceof List<?> values)) throw malformed();
            return values.stream().map(value -> requireType(value, itemType)).toList();
        });
    }

    private <T extends Serializable> CompletableFuture<PageResult<T>> callPage(String command, Serializable body, Duration timeout, Class<T> itemType) {
        return raw(command, body, timeout).thenApply(data -> {
            if (!(data instanceof PageResult<?> page)) throw malformed();
            List<T> items = page.items().stream().map(value -> requireType(value, itemType)).toList();
            return new PageResult<>(items, page.page(), page.pageSize(), page.total());
        });
    }

    private CompletableFuture<Serializable> raw(String command, Serializable body, Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            CompletableFuture<? extends edu.seu.vcampus.common.protocol.ResponseBody<? extends Serializable>> sent =
                    transport.<Serializable>send(command, body, timeout);
            return sent == null
                    ? CompletableFuture.<edu.seu.vcampus.common.protocol.ResponseBody<? extends Serializable>>failedFuture(malformed())
                    : sent;
        }, executor).thenCompose(sent -> sent).handle((response, failure) -> {
            if (failure != null) throw transportFailure(failure);
            if (response == null) throw malformed();
            if (response.success()) {
                if (response.data() == null || !"SUCCESS".equals(response.code())) throw malformed();
                return response.data();
            }
            if (response.code() == null || response.code().isBlank() || response.message() == null || response.message().isBlank()) throw malformed();
            var error = response.error();
            CourseClientException courseFailure = new CourseClientException(response.code(), response.message(),
                    error == null ? null : error.traceId(), error != null && error.retryable());
            throw responseFailure(courseFailure);
        });
    }

    private CourseClientException responseFailure(CourseClientException failure) {
        notifyAuthenticationFailure(failure);
        return failure;
    }

    private void notifyAuthenticationFailure(CourseClientException failure) {
        if (!isAuthenticationFailure(failure.code())) return;
        authenticationFailureListeners.forEach(listener -> listener.accept(failure));
    }

    private static boolean isAuthenticationFailure(String code) {
        return "AUTH_SESSION_EXPIRED".equals(code)
                || "AUTH_ACCOUNT_DISABLED".equals(code)
                || "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED".equals(code);
    }

    private static <T> T requireType(Object value, Class<T> type) {
        if (!type.isInstance(value)) throw malformed();
        return type.cast(value);
    }

    private static CourseClientException network() { return new CourseClientException("COMMON_NETWORK_ERROR", "网络连接异常，请检查连接后重试", null, true); }
    private static CourseClientException timeout() { return new CourseClientException("COMMON_TIMEOUT", "请求超时，请稍后重试", null, true); }
    private CourseClientException transportFailure(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof CourseClientException courseFailure) {
            if (isAuthenticationFailure(courseFailure.code())) {
                notifyAuthenticationFailure(courseFailure);
                return courseFailure;
            }
            if ("COMMON_PROTOCOL_ERROR".equals(courseFailure.code())) {
                return courseFailure;
            }
        }
        return cause instanceof TimeoutException ? timeout() : network();
    }
    private static CourseClientException malformed() { return new CourseClientException("COMMON_PROTOCOL_ERROR", "服务器响应无效，请稍后重试", null, true); }
}
