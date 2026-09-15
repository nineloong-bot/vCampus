package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Widgets, shared helpers and dialog shell for the contact dialog segments. */
abstract class UpdateContactDialogBase extends JDialog {
    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final Border SUBMIT_BORDER = BorderFactory.createCompoundBorder(UiBorders.LINE,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_4,
                    UiSpacing.SPACE_2, UiSpacing.SPACE_4));
    static final Border SUBMIT_FOCUS_BORDER = BorderFactory.createCompoundBorder(UiBorders.FOCUS,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1,
                    UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1));

    final StudentClientService students;
    final Consumer<StudentView> saved;
    final AtomicLong requestGeneration = new AtomicLong();
    final JTextField email = field("student.contact.email", "邮箱");
    final JTextField phone = field("student.contact.phone", "电话");
    final JLabel error = label("student.contact.error", "联系方式提示");
    final JButton refresh = button("刷新数据", "student.contact.refresh");
    final JButton cancel = button("取消", "student.contact.cancel");
    final JButton submit = button("保存", "student.contact.submit");
    StudentView base;
    boolean conflict;
    boolean disposed;
    boolean published;
    boolean initialFocusEstablished;

    UpdateContactDialogBase(Window owner, StudentClientService students,
                               StudentView initial, Consumer<StudentView> saved) {
        super(owner, "修改联系方式", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students, "students");
        this.base = Objects.requireNonNull(initial, "initial");
        this.saved = Objects.requireNonNull(saved, "saved");
        email.setText(initial.email());
        phone.setText(initial.phone());
        email.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { initialFocusEstablished = true; }
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        initializeDialog(owner);
    }

    /** Completes construction once the form, actions and focus order are available. */
    abstract void initializeDialog(Window owner);

    void establishInitialEmailFocus() {
        SwingUtilities.invokeLater(() -> {
            if (isShowing() && !initialFocusEstablished) email.requestFocusInWindow();
        });
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String safeMessage(ResponseBody<?> body, String fallback) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback;
    }

    static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    static JTextField field(String name, String accessibleName) {
        JTextField result = new JTextField(24);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.setBorder(UiBorders.LINE);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    static JButton button(String title, String name) {
        JButton result = new JButton(title);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.getAccessibleContext().setAccessibleName(title);
        return result;
    }

    static JLabel label(String name, String accessibleName) {
        JLabel result = new JLabel(" ");
        result.setName(name);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }
}
