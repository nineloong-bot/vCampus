package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.common.user.UserRole;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** One role-filtered course workspace embedded under the canonical course module. */
public final class CourseWorkspacePanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<Supplier<? extends AbstractCoursePanel>> factories = new ArrayList<>();
    private final Map<Integer, AbstractCoursePanel> loaded = new HashMap<>();
    private final Set<Integer> dirty = new HashSet<>();

    public CourseWorkspacePanel(CourseUiGateway gateway, UserRole role) {
        super(new BorderLayout());
        Objects.requireNonNull(gateway, "gateway");
        setName("page.course");
        setBackground(UiColors.BACKGROUND_PAGE);
        switch (Objects.requireNonNull(role, "role")) {
            case STUDENT -> {
                addTab("教学班查询", () -> new OfferingSearchPanel(gateway));
                addTab("我的选课", () -> new MyEnrollmentPanel(gateway));
                addTab("我的课表", () -> new MySchedulePanel(gateway));
                addTab("退改补", () -> new AdjustmentPanel(gateway));
                addTab("重修", () -> new RetakePanel(gateway));
            }
            case TEACHER -> {
                addTab("教学班查询", () -> new OfferingSearchPanel(gateway));
                addTab("教师课表", () -> new MySchedulePanel(gateway));
            }
            case ADMIN -> {
                addTab("学期管理", () -> new TermManagementPanel(gateway));
                addTab("课程目录", () -> new CourseCatalogPanel(gateway));
                addTab("教学班管理", () -> new OfferingManagementPanel(gateway));
                addTab("修读结果导入", () -> new OutcomeImportPanel(gateway));
                addTab("选退记录", () -> new AdjustmentAuditPanel(gateway));
            }
        }
        tabs.addChangeListener(event -> open(tabs.getSelectedIndex()));
        add(tabs, BorderLayout.CENTER);
        open(0);
    }

    private void addTab(String title, Supplier<? extends AbstractCoursePanel> factory) {
        factories.add(factory);
        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setName("course.tab.placeholder." + (factories.size() - 1));
        placeholder.setOpaque(false);
        tabs.addTab(title, placeholder);
    }

    private void open(int index) {
        if (index < 0) return;
        AbstractCoursePanel page = loaded.computeIfAbsent(index, key -> {
            AbstractCoursePanel created = factories.get(key).get();
            tabs.setComponentAt(key, created);
            return created;
        });
        if (dirty.remove(index)) page.refreshAfterNavigation();
    }
}
