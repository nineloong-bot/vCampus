package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.course.service.CourseClientService;
import edu.seu.vcampus.common.user.UserRole;

import javax.swing.JPanel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

/** Stable page registry consumed by the shared application navigator. */
public final class CourseUiComposition {
    private final Map<String, JPanel> studentPages;
    private final Map<String, JPanel> teacherPages;
    private final Map<String, JPanel> administrativePages;

    public CourseUiComposition(CourseClientService client) {
        this(new CourseClientGateway(client));
    }

    public CourseUiComposition(CourseUiGateway gateway) {
        Objects.requireNonNull(gateway);
        LinkedHashMap<String, JPanel> student = new LinkedHashMap<>();
        student.put("course.offerings", new OfferingSearchPanel(gateway));
        student.put("course.enrollments", new MyEnrollmentPanel(gateway));
        student.put("course.schedule", new MySchedulePanel(gateway));
        student.put("course.adjustment", new AdjustmentPanel(gateway));
        student.put("course.retake", new RetakePanel(gateway));
        studentPages = Collections.unmodifiableMap(new LinkedHashMap<>(student));

        LinkedHashMap<String, JPanel> teacher = new LinkedHashMap<>();
        teacher.put("course.offerings", studentPages.get("course.offerings"));
        teacher.put("course.schedule", studentPages.get("course.schedule"));
        teacherPages = Collections.unmodifiableMap(teacher);

        LinkedHashMap<String, JPanel> admin = new LinkedHashMap<>();
        admin.put("course.terms", new TermManagementPanel(gateway));
        admin.put("course.catalog", new CourseCatalogPanel(gateway));
        admin.put("course.offering-admin", new OfferingManagementPanel(gateway));
        admin.put("course.outcome-import", new OutcomeImportPanel(gateway));
        admin.put("course.adjustment-audit", new AdjustmentAuditPanel(gateway));
        administrativePages = Collections.unmodifiableMap(new LinkedHashMap<>(admin));
    }

    public Map<String, JPanel> studentPages() {
        return studentPages;
    }

    public Map<String, JPanel> administrativePages() {
        return administrativePages;
    }

    /** Returns only the real course pages that the logged-in role may open. */
    public Map<String, JPanel> pagesFor(UserRole role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case STUDENT -> studentPages;
            case TEACHER -> teacherPages;
            case ADMIN -> administrativePages;
        };
    }

    public Map<String, JPanel> allPages() {
        LinkedHashMap<String, JPanel> pages = new LinkedHashMap<>(studentPages);
        pages.putAll(administrativePages);
        return Collections.unmodifiableMap(pages);
    }
}
