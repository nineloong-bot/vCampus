package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.course.service.CourseClientException;
import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CourseView;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.OfferingView;
import edu.seu.vcampus.common.course.TermView;
import edu.seu.vcampus.common.course.UpdateOfferingCommand;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.UserSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Modal create/edit form for an offering aggregate and all of its schedule rows. */
final class OfferingEditorDialog extends JDialog {
    private final UiAsyncGuard asyncGuard = new UiAsyncGuard();
    private final CourseUiGateway gateway;
    private final OfferingSummary existing;
    private final Runnable onSaved;
    private final JComboBox<OfferingReferenceChoice> term = combo("学期");
    private final JComboBox<OfferingReferenceChoice> course = combo("课程");
    private final JComboBox<OfferingReferenceChoice> teacher = combo("教师");
    private final JTextField courseKeyword = field("课程关键字");
    private final JTextField teacherKeyword = field("教师关键字");
    private final JTextField className = field("教学班名称");
    private final JSpinner capacity;
    private final JComboBox<StatusChoice> status = new JComboBox<>(StatusChoice.values());
    private final OfferingScheduleEditorPanel schedules = new OfferingScheduleEditorPanel();
    private final JLabel referenceStatus = label("正在加载学期、课程和教师，请稍候…", UiColors.TEXT_SECONDARY);
    private final JLabel error = label(" ", UiColors.ACCENT);
    private final JButton retry = AbstractCoursePanel.secondary("重试加载");
    private final JButton save;
    private long referenceSequence;
    private boolean referenceReady;
    private boolean active = true;
    private OfferingReferenceChoice resolvedExistingTeacher;

    OfferingEditorDialog(Window owner, CourseUiGateway gateway, OfferingSummary existing, Runnable onSaved) {
        super(owner, existing == null ? "新建教学班" : "编辑教学班", ModalityType.APPLICATION_MODAL);
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.existing = existing;
        this.onSaved = Objects.requireNonNull(onSaved, "onSaved");
        int minimumCapacity = existing == null ? 1 : Math.max(1, existing.enrolledCount());
        int initialCapacity = existing == null ? 40 : Math.max(minimumCapacity, existing.capacity());
        capacity = spinner(initialCapacity, minimumCapacity, Math.max(10_000, initialCapacity), "容量");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, UiSpacing.LG));
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.XL, UiSpacing.XL, UiSpacing.XL, UiSpacing.XL));
        root.add(title(), BorderLayout.NORTH);
        root.add(form(), BorderLayout.CENTER);
        save = AbstractCoursePanel.primary(existing == null ? "创建教学班" : "保存修改");
        save.setEnabled(false);
        save.addActionListener(event -> submit());
        retry.setEnabled(false);
        retry.addActionListener(event -> loadReferences());
        root.add(actions(), BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(save);
        if (existing == null) schedules.addDefaultRow();
        else fill(existing);
        setSize(new Dimension(840, 780));
        setLocationRelativeTo(owner);
        loadReferences();
    }

    private JPanel title() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel heading = label(existing == null ? "新建教学班" : "编辑教学班", UiColors.TEXT_PRIMARY);
        heading.setFont(UiTypography.PAGE_TITLE);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(label("从学期、课程和在职教师中选择，并逐行维护上课安排", UiColors.TEXT_SECONDARY));
        return panel;
    }

    private JScrollPane form() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(referenceLine());
        panel.add(pair("学期（必填）", term, "课程（必填）", course));
        panel.add(searchLine("课程关键字", courseKeyword, "查询课程"));
        panel.add(pair("教师（必填）", teacher, "教学班名称（必填）", className));
        panel.add(searchLine("教师关键字", teacherKeyword, "查询教师"));
        status.setFont(UiTypography.BODY);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        status.getAccessibleContext().setAccessibleName("教学班状态");
        panel.add(pair("容量（必填）", capacity, "教学班状态", status));
        panel.add(label("上课安排（必填）", UiColors.TEXT_PRIMARY));
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(schedules);
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel referenceLine() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(referenceStatus);
        panel.add(Box.createHorizontalGlue());
        panel.add(retry);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        return panel;
    }

    private JPanel searchLine(String caption, JTextField keyword, String actionText) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(label(caption, UiColors.TEXT_SECONDARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(keyword);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton action = AbstractCoursePanel.secondary(actionText);
        action.addActionListener(event -> loadReferences());
        panel.add(action);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        return panel;
    }

    private JPanel pair(String leftLabel, java.awt.Component left, String rightLabel, java.awt.Component right) {
        JPanel pair = new JPanel(new java.awt.GridLayout(1, 2, UiSpacing.LG, 0));
        pair.setOpaque(false);
        pair.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        pair.add(row(leftLabel, left));
        pair.add(row(rightLabel, right));
        return pair;
    }

    private JPanel row(String text, java.awt.Component input) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        JLabel caption = label(text, UiColors.TEXT_PRIMARY);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        row.add(caption);
        row.add(Box.createVerticalStrut(UiSpacing.SM));
        if (input instanceof javax.swing.JComponent component) component.setAlignmentX(LEFT_ALIGNMENT);
        row.add(input);
        row.add(Box.createVerticalStrut(UiSpacing.MD));
        return row;
    }

    private JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(error);
        panel.add(Box.createHorizontalGlue());
        JButton cancel = AbstractCoursePanel.secondary("取消");
        cancel.addActionListener(event -> dispose());
        panel.add(cancel);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(save);
        return panel;
    }

    private void loadReferences() {
        referenceReady = false;
        save.setEnabled(false);
        retry.setEnabled(false);
        referenceStatus.setText("正在加载学期、课程和教师，请稍候…");
        error.setText(" ");
        long request = ++referenceSequence;
        String courseSearch = courseKeyword.getText().trim();
        String teacherSearch = teacherKeyword.getText().trim();
        if (courseSearch.isEmpty() && existing != null) courseSearch = existing.courseCode();

        CompletableFuture<String> selectedTerm = existing == null
                ? gateway.currentTermId() : CompletableFuture.completedFuture(existing.termId());
        CompletableFuture<Optional<UserSummary>> existingTeacher = existing == null
                || resolvedExistingTeacher != null
                ? CompletableFuture.completedFuture(Optional.empty())
                : gateway.resolveTeacher(existing.teacherUserId());
        CompletableFuture<ReferenceData> loaded = gateway.listTerms()
                .thenCombine(selectedTerm, TermAndCurrent::new)
                .thenCombine(gateway.searchCatalog(new CourseCatalogQuery(courseSearch, true, 0, 100)),
                        (termData, courses) -> new PartialReferenceData(termData, courses))
                .thenCombine(gateway.searchTeachers(teacherSearch),
                        (partial, teachers) -> new ReferenceData(
                                partial.termData(), partial.courses(), teachers, Optional.empty()))
                .thenCombine(existingTeacher, (data, resolved) -> new ReferenceData(
                        data.termData(), data.courses(), data.teachers(), resolved));
        loaded.whenComplete((data, failure) -> SwingUtilities.invokeLater(() -> {
            if (!active || referenceSequence != request) return;
            if (failure != null) {
                referenceStatus.setText("参考数据加载失败，请重试");
                retry.setEnabled(true);
                return;
            }
            installReferences(data);
            referenceReady = term.getSelectedItem() != null
                    && course.getSelectedItem() != null && teacher.getSelectedItem() != null;
            referenceStatus.setText(referenceReady
                    ? "参考数据已就绪" : "请选择有结果的学期、课程和教师");
            save.setEnabled(referenceReady);
        }));
    }

    private void installReferences(ReferenceData data) {
        data.existingTeacher().ifPresent(value -> resolvedExistingTeacher =
                new OfferingReferenceChoice(value.userId(), value.loginId()));
        OfferingReferenceChoice currentTerm = selectedChoice(term);
        OfferingReferenceChoice currentCourse = selectedChoice(course);
        OfferingReferenceChoice currentTeacher = selectedChoice(teacher);
        String desiredTerm = currentTerm == null && existing != null ? existing.termId() : id(currentTerm);
        String desiredCourse = currentCourse == null && existing != null ? existing.courseId() : id(currentCourse);
        String desiredTeacher = currentTeacher == null && existing != null ? existing.teacherUserId() : id(currentTeacher);
        if (desiredTerm == null) desiredTerm = data.termData().currentTermId();

        List<OfferingReferenceChoice> terms = data.termData().terms().stream()
                .map(value -> new OfferingReferenceChoice(value.termId(), value.termName() + " · " + value.termCode()))
                .toList();
        List<OfferingReferenceChoice> courses = data.courses().items().stream()
                .map(value -> new OfferingReferenceChoice(value.courseId(), value.courseCode() + " · " + value.courseName()))
                .toList();
        List<OfferingReferenceChoice> teachers = data.teachers().items().stream()
                .map(value -> new OfferingReferenceChoice(value.userId(), value.loginId()))
                .toList();
        installChoices(term, terms, desiredTerm,
                currentTerm != null ? currentTerm : existing == null
                        ? null : new OfferingReferenceChoice(existing.termId(), existing.termId()));
        installChoices(course, courses, desiredCourse,
                currentCourse != null ? currentCourse : existing == null ? null
                        : new OfferingReferenceChoice(existing.courseId(),
                                existing.courseCode() + " · " + existing.courseName()));
        installChoices(teacher, teachers, desiredTeacher,
                currentTeacher != null ? currentTeacher : existing == null
                        ? null : resolvedExistingTeacher);
    }

    private static void installChoices(JComboBox<OfferingReferenceChoice> combo,
                                       List<OfferingReferenceChoice> values,
                                       String desiredId,
                                       OfferingReferenceChoice fallback) {
        List<OfferingReferenceChoice> choices = new ArrayList<>(values);
        boolean found = desiredId != null && choices.stream().anyMatch(value -> desiredId.equals(value.id()));
        if (!found && fallback != null && desiredId.equals(fallback.id())) choices.add(0, fallback);
        combo.removeAllItems();
        choices.forEach(combo::addItem);
        selectId(combo, desiredId);
    }

    private void submit() {
        error.setText(" ");
        if (!referenceReady) {
            error.setText("请等待参考数据加载完成后再保存");
            return;
        }
        CompletableFuture<OfferingView> request;
        try {
            String cleanTerm = requiredChoice(term, "请选择学期");
            String cleanCourse = requiredChoice(course, "请选择课程");
            String cleanTeacher = requiredChoice(teacher, "请选择教师");
            String cleanClass = required(className, "请输入教学班名称");
            int cleanCapacity = ((Number) capacity.getValue()).intValue();
            if (existing != null && cleanCapacity < existing.enrolledCount()) {
                throw new IllegalArgumentException("容量不能小于当前已选人数 " + existing.enrolledCount());
            }
            StatusChoice cleanStatus = (StatusChoice) status.getSelectedItem();
            List<CreateOfferingCommand.ScheduleInput> cleanSchedules = schedules.scheduleInputs();
            if (existing == null) {
                request = gateway.createOffering(new CreateOfferingCommand(cleanTerm, cleanCourse, cleanTeacher,
                        cleanClass, cleanCapacity, cleanStatus.code(), cleanSchedules));
            } else {
                request = gateway.updateOffering(new UpdateOfferingCommand(existing.offeringId(), cleanTerm,
                        cleanCourse, cleanTeacher, cleanClass, cleanCapacity, cleanStatus.code(),
                        existing.rowVersion(), cleanSchedules));
            }
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage() == null ? "请检查教学班字段和上课安排" : invalid.getMessage());
            return;
        }
        String idle = save.getText();
        save.setEnabled(false);
        save.setText(existing == null ? "正在创建…" : "正在保存…");
        long asyncRequest = asyncGuard.begin();
        request.whenComplete((saved, failure) -> SwingUtilities.invokeLater(() -> {
            if (!asyncGuard.accepts(asyncRequest)) return;
            save.setEnabled(referenceReady);
            save.setText(idle);
            if (failure != null) { error.setText(saveFailure(failure)); return; }
            onSaved.run();
            dispose();
        }));
    }

    @Override public void dispose() {
        active = false;
        referenceSequence++;
        asyncGuard.deactivate();
        super.dispose();
    }

    private void fill(OfferingSummary value) {
        className.setText(value.className());
        capacity.setValue(value.capacity());
        status.setSelectedItem(StatusChoice.fromCode(value.offeringStatus()));
        schedules.setSchedules(value.schedules());
    }

    private static String saveFailure(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof CourseClientException clientFailure) {
            if ("COMMON_CONCURRENT_MODIFICATION".equals(clientFailure.code())) {
                return "教学班已被其他管理员修改，请刷新并核对最新记录后重试";
            }
            String safe = clientFailure.getMessage() == null || clientFailure.getMessage().isBlank()
                    ? "服务器未提供可显示的错误信息" : clientFailure.getMessage();
            if (clientFailure.traceId() != null && !clientFailure.traceId().isBlank()) {
                return "保存失败：" + safe + "（跟踪编号：" + clientFailure.traceId() + "）";
            }
            return "保存失败：" + safe;
        }
        return "保存失败，请检查连接后重试";
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }

    private static OfferingReferenceChoice selectedChoice(JComboBox<OfferingReferenceChoice> combo) {
        Object selected = combo.getSelectedItem();
        return selected instanceof OfferingReferenceChoice choice ? choice : null;
    }

    private static String id(OfferingReferenceChoice choice) {
        return choice == null ? null : choice.id();
    }

    private static void selectId(JComboBox<OfferingReferenceChoice> combo, String id) {
        if (id == null) return;
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (id.equals(combo.getItemAt(index).id())) {
                combo.setSelectedIndex(index);
                return;
            }
        }
    }

    private static String requiredChoice(JComboBox<OfferingReferenceChoice> combo, String message) {
        Object selected = combo.getSelectedItem();
        if (!(selected instanceof OfferingReferenceChoice choice)) throw new IllegalArgumentException(message);
        return choice.id();
    }

    private static JTextField field(String name) {
        JTextField field = new JTextField();
        field.setFont(UiTypography.BODY);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        field.setPreferredSize(new Dimension(280, UiDimensions.CONTROL_HEIGHT));
        field.getAccessibleContext().setAccessibleName(name);
        return field;
    }

    private static <T> JComboBox<T> combo(String name) {
        JComboBox<T> combo = new JComboBox<>();
        combo.setFont(UiTypography.BODY);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        combo.getAccessibleContext().setAccessibleName(name);
        return combo;
    }

    private static JSpinner spinner(int value, int minimum, int maximum, String name) {
        JSpinner spinner = new JSpinner(new BoundedIntegerSpinnerModel(value, minimum, maximum));
        spinner.setFont(UiTypography.BODY);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        spinner.getAccessibleContext().setAccessibleName(name);
        return spinner;
    }

    private static String required(JTextField field, String message) {
        String value = field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(message);
        return value;
    }

    private static JLabel label(String text, java.awt.Color color) {
        JLabel label = new JLabel(text);
        label.setFont(UiTypography.BODY);
        label.setForeground(color);
        return label;
    }

    private enum StatusChoice {
        DRAFT("DRAFT", "草稿"), OPEN("OPEN", "开放"), CLOSED("CLOSED", "已关闭"),
        CANCELLED("CANCELLED", "已取消");

        private final String code;
        private final String label;

        StatusChoice(String code, String label) { this.code = code; this.label = label; }
        String code() { return code; }
        static StatusChoice fromCode(String code) {
            for (StatusChoice value : values()) if (value.code.equals(code)) return value;
            throw new IllegalArgumentException("不支持的教学班状态：" + code);
        }
        @Override public String toString() { return label; }
    }

    private record TermAndCurrent(List<TermView> terms, String currentTermId) { }
    private record PartialReferenceData(TermAndCurrent termData, PageResult<CourseView> courses) { }
    private record ReferenceData(TermAndCurrent termData, PageResult<CourseView> courses,
                                 PageResult<UserSummary> teachers,
                                 Optional<UserSummary> existingTeacher) { }

    private static final class BoundedIntegerSpinnerModel extends SpinnerNumberModel {
        private final int minimum;
        private final int maximum;

        private BoundedIntegerSpinnerModel(int value, int minimum, int maximum) {
            super(value, minimum, maximum, 1);
            this.minimum = minimum;
            this.maximum = maximum;
        }

        @Override public void setValue(Object value) {
            if (!(value instanceof Number number)) throw new IllegalArgumentException("容量必须是整数");
            int candidate = number.intValue();
            if (candidate < minimum || candidate > maximum) {
                throw new IllegalArgumentException("容量必须在 " + minimum + " 到 " + maximum + " 之间");
            }
            super.setValue(candidate);
        }
    }
}
