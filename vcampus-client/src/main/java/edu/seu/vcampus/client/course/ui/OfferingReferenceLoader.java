package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.autocomplete.AutocompleteChoice;
import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.user.AccountStatus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Maps course and teacher gateway searches into stable autocomplete choices. */
public final class OfferingReferenceLoader {
    private static final int MAX_CHOICES = 8;
    private final CourseUiGateway gateway;

    /** Creates a loader backed by the course UI gateway. */
    public OfferingReferenceLoader(CourseUiGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    /** Finds enabled courses by code or name. */
    public CompletableFuture<List<AutocompleteChoice>> searchCourses(String query, int limit) {
        int capped = Math.min(MAX_CHOICES, Math.max(1, limit));
        return gateway.searchCatalog(new CourseCatalogQuery(query == null ? "" : query.strip(), true, 0, capped))
                .thenApply(page -> page.items().stream().filter(course -> course.active()).limit(capped)
                        .map(course -> new AutocompleteChoice(course.courseId(),
                                course.courseCode() + " · " + course.courseName(),
                                course.credit().stripTrailingZeros().toPlainString() + " 学分"))
                        .toList());
    }

    /** Finds active teachers by name, staff number, or account identifier. */
    public CompletableFuture<List<AutocompleteChoice>> searchTeachers(String query, int limit) {
        int capped = Math.min(MAX_CHOICES, Math.max(1, limit));
        return gateway.searchTeachers(query == null ? "" : query.strip()).thenApply(page -> page.items().stream()
                .filter(teacher -> teacher.accountStatus() == AccountStatus.ACTIVE).limit(capped)
                .map(teacher -> new AutocompleteChoice(teacher.userId(), teacher.loginId(), "在职教师"))
                .toList());
    }
}
