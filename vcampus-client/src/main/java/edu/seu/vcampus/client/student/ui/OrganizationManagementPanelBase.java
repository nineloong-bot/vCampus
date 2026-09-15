package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** Shared state and small helpers for the organization management panel segments. */
abstract class OrganizationManagementPanelBase extends JPanel {
    static final Pattern MAJOR_CODE = Pattern.compile("^[0-9A-Z]{3}$");

    protected final StudentClientService students;
    protected final ClientConnection connection;
    protected final boolean departmentManagementAllowed;
    protected final boolean classManagementAllowed;
    protected final AtomicLong requestGeneration = new AtomicLong();
    protected volatile boolean active;

    protected final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("组织架构");
    protected final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    protected final JTree tree = new JTree(treeModel);
    protected final JLabel statusLabel = new JLabel("正在加载");
    protected final JLabel errorLabel = new JLabel(" ");
    protected final JButton addDeptButton = new JButton("新增学院");
    protected final JButton addMajorButton = new JButton("新增专业");
    protected final JButton addClassButton = new JButton("新增班级");
    protected final JButton addStudentButton = new JButton("新增学生");
    protected final JButton batchAssignButton = new JButton("批量分班");
    protected final JPanel editPanel = new JPanel(new BorderLayout());

    protected DefaultMutableTreeNode selectedNode;
    protected Object editingTarget;
    protected boolean isNewItem;

    /** Stores the service and connection shared by the panel segments. */
    protected OrganizationManagementPanelBase(StudentClientService students, ClientConnection connection,
            boolean departmentManagementAllowed, boolean classManagementAllowed) {
        this.students = Objects.requireNonNull(students, "students");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.departmentManagementAllowed = departmentManagementAllowed;
        this.classManagementAllowed = classManagementAllowed;
    }

    void connectionChanged(ConnectionState state) {
        onEdt(() -> {
            if (!active) return;
            updateAddButtons();
        });
    }

    void updateAddButtons() {
        boolean connected = connection.state() == ConnectionState.CONNECTED;
        boolean none = editingTarget == null;
        boolean isDept = editingTarget instanceof DepartmentView;
        boolean isMajor = editingTarget instanceof MajorView;
        boolean isYear = editingTarget instanceof YearNode;
        boolean isClass = editingTarget instanceof ClassView;
        addDeptButton.setEnabled(departmentManagementAllowed && connected && none);
        addMajorButton.setEnabled(connected && isDept);
        if (classManagementAllowed) {
            addClassButton.setEnabled(connected && (isMajor || isYear));
            addStudentButton.setEnabled(connected && isClass);
            batchAssignButton.setEnabled(connected && (isMajor || isYear));
        }
    }

    void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    JPanel buildEditForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        return form;
    }

    void addFormRow(JPanel form, GridBagConstraints c, String label, Component field, int row) {
        c.gridy = row; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.gridwidth = 1;
        JLabel l = new JLabel(label);
        l.setFont(UiTypography.CAPTION);
        l.setForeground(UiColors.TEXT_SECONDARY);
        form.add(l, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
    }

    JTextField editField(String name, String value) {
        JTextField field = new JTextField(value, 20);
        field.setName(name);
        field.setFont(UiTypography.BODY);
        field.setBorder(UiBorders.LINE);
        return field;
    }

    JButton saveButton() {
        JButton button = new JButton("保存");
        button.setName("student.org.save");
        button.setFont(UiTypography.BODY);
        button.getAccessibleContext().setAccessibleName("保存");
        return button;
    }

    static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    static String safeMessage(ResponseBody<?> body) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : "操作失败，请稍后重试";
    }
}
