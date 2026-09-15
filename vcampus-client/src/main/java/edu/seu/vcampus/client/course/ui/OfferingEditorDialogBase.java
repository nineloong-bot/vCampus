package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.CourseView;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.TermView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.user.UserSummary;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Widgets, shared collaborators and dialog shell for the offering editor segments. */
abstract class OfferingEditorDialogBase extends JDialog {
    final UiAsyncGuard asyncGuard = new UiAsyncGuard();
    final CourseUiGateway gateway;
    final OfferingSummary existing;
    final Runnable onSaved;
    final JComboBox<OfferingReferenceChoice> term = combo("学期");
    final JComboBox<OfferingReferenceChoice> course = combo("课程");
    final JComboBox<OfferingReferenceChoice> teacher = combo("教师");
    final JTextField courseKeyword = field("课程关键字");
    final JTextField teacherKeyword = field("教师关键字");
    final JTextField className = field("教学班名称");
    final JSpinner capacity;
    final JSpinner retakeCapacity;
    final JComboBox<StatusChoice> status = new JComboBox<>(StatusChoice.values());
    final OfferingScheduleEditorPanel schedules = new OfferingScheduleEditorPanel();
    final JLabel referenceStatus = label("正在加载学期、课程和教师，请稍候…", UiColors.TEXT_SECONDARY);
    final JLabel error = label(" ", UiColors.ACCENT);
    final JButton retry = AbstractCoursePanel.secondary("重试加载");
    final JButton save;
    long referenceSequence;
    boolean referenceReady;
    boolean active = true;
    OfferingReferenceChoice resolvedExistingTeacher;

    OfferingEditorDialogBase(Window owner, CourseUiGateway gateway, OfferingSummary existing,
            Runnable onSaved) {
        super(owner, existing == null ? "新建教学班" : "编辑教学班", ModalityType.APPLICATION_MODAL);
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.existing = existing;
        this.onSaved = Objects.requireNonNull(onSaved, "onSaved");
        int minimumCapacity = existing == null ? 1 : Math.max(1, existing.enrolledCount());
        int initialCapacity = existing == null ? 40 : Math.max(minimumCapacity, existing.capacity());
        capacity = spinner(initialCapacity, minimumCapacity, Math.max(10_000, initialCapacity), "容量");
        int minimumRetakeCapacity = existing == null ? 0 : existing.retakeEnrolledCount();
        int initialRetakeCapacity = existing == null ? 5
                : Math.max(minimumRetakeCapacity, existing.retakeCapacity());
        retakeCapacity = spinner(initialRetakeCapacity, minimumRetakeCapacity,
                Math.max(10_000, initialRetakeCapacity), "重修容量");
        save = AbstractCoursePanel.primary(existing == null ? "创建教学班" : "保存修改");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initializeDialog(owner);
    }

    /** Completes construction once the layout, loaders and actions are available. */
    abstract void initializeDialog(Window owner);

    static JTextField field(String name) {
        JTextField field = new JTextField();
        field.setFont(UiTypography.BODY);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        field.setPreferredSize(new Dimension(280, UiDimensions.CONTROL_HEIGHT));
        field.getAccessibleContext().setAccessibleName(name);
        return field;
    }

    static <T> JComboBox<T> combo(String name) {
        JComboBox<T> combo = new JComboBox<>();
        combo.setFont(UiTypography.BODY);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        combo.getAccessibleContext().setAccessibleName(name);
        return combo;
    }

    static JSpinner spinner(int value, int minimum, int maximum, String name) {
        JSpinner spinner = new JSpinner(new BoundedIntegerSpinnerModel(value, minimum, maximum));
        spinner.setFont(UiTypography.BODY);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        spinner.getAccessibleContext().setAccessibleName(name);
        return spinner;
    }

    static JLabel label(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(UiTypography.BODY);
        label.setForeground(color);
        return label;
    }

    /** Offering status codes paired with their display labels. */
    enum StatusChoice {
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

    /** Terms plus the term the editor should preselect. */
    record TermAndCurrent(List<TermView> terms, String currentTermId) { }
    /** Terms paired with the catalog page, before teachers are loaded. */
    record PartialReferenceData(TermAndCurrent termData, PageResult<CourseView> courses) { }
    /** Every reference the offering editor needs to install its choices. */
    record ReferenceData(TermAndCurrent termData, PageResult<CourseView> courses,
                                 PageResult<UserSummary> teachers,
                                 Optional<UserSummary> existingTeacher) { }

    /** Spinner model that rejects values outside its configured bounds. */
    static final class BoundedIntegerSpinnerModel extends SpinnerNumberModel {
        private final int minimum;
        private final int maximum;

        BoundedIntegerSpinnerModel(int value, int minimum, int maximum) {
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
