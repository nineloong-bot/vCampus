package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.common.student.UpdateStudentContactCommand;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Modal editor for the signed-in student's contact details. */
public final class UpdateContactDialog extends JDialog {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Border SUBMIT_BORDER = BorderFactory.createCompoundBorder(UiBorders.LINE,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_4,
                    UiSpacing.SPACE_2, UiSpacing.SPACE_4));
    private static final Border SUBMIT_FOCUS_BORDER = BorderFactory.createCompoundBorder(UiBorders.FOCUS,
            BorderFactory.createEmptyBorder(UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1,
                    UiSpacing.SPACE_2 - 1, UiSpacing.SPACE_4 - 1));

    private final StudentClientService students;
    private final Consumer<StudentView> saved;
    private final AtomicLong requestGeneration = new AtomicLong();
    private final JTextField email = field("student.contact.email", "邮箱");
    private final JTextField phone = field("student.contact.phone", "电话");
    private final JLabel error = label("student.contact.error", "联系方式提示");
    private final JButton refresh = button("刷新数据", "student.contact.refresh");
    private final JButton cancel = button("取消", "student.contact.cancel");
    private final JButton submit = button("保存", "student.contact.submit");
    private StudentView base;
    private boolean conflict;
    private boolean disposed;
    private boolean published;
    private boolean initialFocusEstablished;

    public UpdateContactDialog(Window owner, StudentClientService students,
                               StudentView initial, Consumer<StudentView> saved) {
        super(owner, "修改联系方式", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students, "students");
        this.base = Objects.requireNonNull(initial, "initial");
        this.saved = Objects.requireNonNull(saved, "saved");
        email.setText(initial.email());
        phone.setText(initial.phone());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildForm());
        refresh.setVisible(false);
        refresh.addActionListener(event -> refreshBase());
        cancel.addActionListener(event -> dispose());
        submit.addActionListener(event -> save());
        getRootPane().setDefaultButton(submit);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new ContactFocusTraversalPolicy());
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) { establishInitialEmailFocus(); }
            @Override public void windowActivated(WindowEvent event) { establishInitialEmailFocus(); }
        });
        setSize(new Dimension(560, 360));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm() {
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        content.setBackground(UiColors.BACKGROUND_PAGE);
        content.setBorder(UiBorders.pageInset());
        JLabel title = new JLabel("修改联系方式");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        content.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        constraints.anchor = GridBagConstraints.WEST;
        addField(form, constraints, "邮箱", email, 0);
        addField(form, constraints, "电话", phone, 1);
        content.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, UiSpacing.SPACE_2));
        bottom.setOpaque(false);
        error.setForeground(UiColors.ERROR_FG);
        error.setFont(UiTypography.CAPTION);
        bottom.add(error, BorderLayout.NORTH);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        left.setOpaque(false);
        left.add(cancel);
        left.add(refresh);
        bottom.add(left, BorderLayout.WEST);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        submit.setUI(new BasicButtonUI());
        submit.setOpaque(true);
        submit.setContentAreaFilled(true);
        submit.setBorder(SUBMIT_BORDER);
        submit.setFocusPainted(true);
        submit.setBackground(UiColors.ACCENT);
        submit.setForeground(UiColors.TEXT_ON_PRIMARY);
        submit.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { submit.setBorder(SUBMIT_FOCUS_BORDER); }
            @Override public void focusLost(FocusEvent event) { submit.setBorder(SUBMIT_BORDER); }
        });
        right.add(submit);
        bottom.add(right, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);
        return content;
    }

    private static void addField(JPanel form, GridBagConstraints constraints,
                                 String title, JTextField value, int row) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        JLabel label = new JLabel(title);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        form.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, 0);
        form.add(value, constraints);
        constraints.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
    }

    private void save() {
        if (disposed || conflict) return;
        String normalizedEmail = blankToNull(email.getText());
        String normalizedPhone = blankToNull(phone.getText());
        if (normalizedEmail != null && !EMAIL.matcher(normalizedEmail).matches()) {
            error.setText("请输入格式正确的邮箱地址");
            email.requestFocusInWindow();
            return;
        }
        long generation = requestGeneration.incrementAndGet();
        setSaving(true);
        CompletableFuture<ResponseBody<StudentView>> response;
        try {
            response = students.updateContact(new UpdateStudentContactCommand(
                    base.studentId(), normalizedEmail, normalizedPhone, base.rowVersion()));
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> finishSave(generation, body, failure)));
    }

    private void finishSave(long generation, ResponseBody<StudentView> body, Throwable failure) {
        if (disposed || generation != requestGeneration.get()) return;
        if (failure != null) {
            setSaving(false);
            error.setText("保存失败，请稍后重试");
            return;
        }
        if (body != null && body.success() && body.data() != null) {
            if (published) return;
            published = true;
            dispose();
            saved.accept(body.data());
            return;
        }
        setSaving(false);
        if (body != null && "COMMON_CONCURRENT_MODIFICATION".equals(body.code())) {
            conflict = true;
            submit.setEnabled(false);
            refresh.setVisible(true);
            error.setText("数据已被修改，请刷新数据后确认再保存");
            return;
        }
        error.setText(safeMessage(body, "保存失败，请稍后重试"));
    }

    private void refreshBase() {
        if (disposed || !conflict) return;
        long generation = requestGeneration.incrementAndGet();
        refresh.setEnabled(false);
        refresh.setText("正在刷新");
        CompletableFuture<ResponseBody<StudentView>> response;
        try {
            response = students.getCurrent();
        } catch (RuntimeException failure) {
            response = CompletableFuture.failedFuture(failure);
        }
        response.whenComplete((body, failure) -> onEdt(() -> finishRefresh(generation, body, failure)));
    }

    private void finishRefresh(long generation, ResponseBody<StudentView> body, Throwable failure) {
        if (disposed || generation != requestGeneration.get()) return;
        refresh.setText("刷新数据");
        if (failure == null && body != null && body.success() && body.data() != null) {
            base = body.data();
            conflict = false;
            refresh.setVisible(false);
            refresh.setEnabled(true);
            submit.setEnabled(true);
            error.setText("数据已刷新，请确认后保存");
            return;
        }
        refresh.setEnabled(true);
        error.setText(failure == null ? safeMessage(body, "刷新失败，请稍后重试") : "刷新失败，请稍后重试");
    }

    private void setSaving(boolean saving) {
        email.setEnabled(!saving);
        phone.setEnabled(!saving);
        submit.setEnabled(!saving);
        cancel.setEnabled(!saving);
        refresh.setEnabled(!saving);
        submit.setText(saving ? "正在保存" : "保存");
        if (saving) error.setText(" ");
    }

    @Override public void dispose() {
        disposed = true;
        requestGeneration.incrementAndGet();
        super.dispose();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String safeMessage(ResponseBody<?> body, String fallback) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : fallback;
    }

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    private void establishInitialEmailFocus() {
        SwingUtilities.invokeLater(() -> {
            if (isShowing() && !initialFocusEstablished) {
                initialFocusEstablished = email.requestFocusInWindow();
            }
        });
    }

    private static JTextField field(String name, String accessibleName) {
        JTextField result = new JTextField(24);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.setBorder(UiBorders.LINE);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    private static JButton button(String title, String name) {
        JButton result = new JButton(title);
        result.setName(name);
        result.setFont(UiTypography.BODY);
        result.getAccessibleContext().setAccessibleName(title);
        return result;
    }

    private static JLabel label(String name, String accessibleName) {
        JLabel result = new JLabel(" ");
        result.setName(name);
        result.getAccessibleContext().setAccessibleName(accessibleName);
        return result;
    }

    private final class ContactFocusTraversalPolicy extends FocusTraversalPolicy {
        private List<Component> order() {
            List<Component> components = new ArrayList<>(List.of(email, phone, cancel));
            if (refresh.isVisible()) components.add(refresh);
            components.add(submit);
            return components;
        }

        @Override public Component getComponentAfter(Container root, Component current) {
            List<Component> components = order();
            return components.get((components.indexOf(current) + 1 + components.size()) % components.size());
        }
        @Override public Component getComponentBefore(Container root, Component current) {
            List<Component> components = order();
            return components.get((components.indexOf(current) - 1 + components.size()) % components.size());
        }
        @Override public Component getFirstComponent(Container root) { return order().getFirst(); }
        @Override public Component getLastComponent(Container root) { return order().getLast(); }
        @Override public Component getDefaultComponent(Container root) { return email; }
    }
}
