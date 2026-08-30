package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.common.user.UserRole;

import javax.swing.JPanel;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/** Stable page registry consumed by the shared application navigator. */
public final class CourseUiComposition {
    private final CourseUiGateway gateway;
    private final Map<String, JPanel> instantiatedPages = new HashMap<>();
    private Map<String, JPanel> studentPages;
    private Map<String, JPanel> teacherPages;
    private Map<String, JPanel> administrativePages;
    private Map<String, JPanel> allPages;

    public CourseUiComposition(CourseClientService client) {
        this(new CourseClientGateway(client));
    }

    public CourseUiComposition(CourseUiGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    public synchronized Map<String, JPanel> studentPages() {
        if (studentPages == null) {
            studentPages = pages("course.offerings", "course.enrollments", "course.schedule", "course.adjustment", "course.retake");
        }
        return studentPages;
    }

    public synchronized Map<String, JPanel> administrativePages() {
        if (administrativePages == null) {
            administrativePages = pages("course.terms", "course.catalog", "course.offering-admin", "course.outcome-import", "course.adjustment-audit");
        }
        return administrativePages;
    }

    /** Returns only the real course pages that the logged-in role may open. */
    public synchronized Map<String, JPanel> pagesFor(UserRole role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case STUDENT -> studentPages();
            case TEACHER -> teacherPages();
            case ADMIN -> administrativePages();
        };
    }

    public synchronized Map<String, JPanel> allPages() {
        if (allPages != null) return allPages;
        LinkedHashMap<String, JPanel> pages = new LinkedHashMap<>(studentPages());
        pages.putAll(administrativePages());
        allPages = Collections.unmodifiableMap(pages);
        return allPages;
    }

    private Map<String, JPanel> teacherPages() {
        if (teacherPages == null) teacherPages = pages("course.offerings", "course.schedule");
        return teacherPages;
    }

    private Map<String, JPanel> pages(String... pageIds) {
        LinkedHashMap<String, JPanel> pages = new LinkedHashMap<>();
        for (String pageId : pageIds) pages.put(pageId, page(pageId));
        return Collections.unmodifiableMap(pages);
    }

    private JPanel page(String pageId) {
        return instantiatedPages.computeIfAbsent(pageId, this::createPage);
    }

    private JPanel createPage(String pageId) {
        return switch (pageId) {
            case "course.offerings" -> new OfferingSearchPanel(gateway);
            case "course.enrollments" -> new MyEnrollmentPanel(gateway);
            case "course.schedule" -> new MySchedulePanel(gateway);
            case "course.adjustment" -> new AdjustmentPanel(gateway);
            case "course.retake" -> new RetakePanel(gateway);
            case "course.terms" -> new TermManagementPanel(gateway);
            case "course.catalog" -> new CourseCatalogPanel(gateway);
            case "course.offering-admin" -> new OfferingManagementPanel(gateway);
            case "course.outcome-import" -> new OutcomeImportPanel(gateway);
            case "course.adjustment-audit" -> new AdjustmentAuditPanel(gateway);
            default -> throw new IllegalArgumentException("Unknown course page: " + pageId);
        };
    }
}
