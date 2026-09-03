package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class OrganizationManagementPanel extends JPanel {
    private static final Pattern MAJOR_CODE = Pattern.compile("^[0-9A-Z]{3}$");

    private final StudentClientService students;
    private final ClientConnection connection;
    private final AtomicLong requestGeneration = new AtomicLong();
    private volatile boolean active;

    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("组织架构");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);
    private final JLabel statusLabel = new JLabel("正在加载");
    private final JLabel errorLabel = new JLabel(" ");
    private final JButton addDeptButton = new JButton("新增学院");
    private final JButton addMajorButton = new JButton("新增专业");
    private final JButton addClassButton = new JButton("新增班级");
    private final JButton addStudentButton = new JButton("新增学生");
    private final JPanel editPanel = new JPanel(new BorderLayout());

    private DefaultMutableTreeNode selectedNode;
    private Object editingTarget;
    private boolean isNewItem;

    public OrganizationManagementPanel(StudentClientService students, ClientConnection connection) {
        super(new BorderLayout(UiSpacing.SPACE_4, 0));
        this.students = Objects.requireNonNull(students, "students");
        this.connection = Objects.requireNonNull(connection, "connection");
        setName("student.org");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        buildPage();
        connection.addStateListener(this::connectionChanged);
    }

    private void buildPage() {
        JPanel left = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        left.setOpaque(false);

        JPanel heading = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        heading.setOpaque(false);
        JLabel title = new JLabel("组织架构管理");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        title.setName("student.org.title");
        heading.add(title, BorderLayout.NORTH);
        statusLabel.setFont(UiTypography.CAPTION);
        statusLabel.setForeground(UiColors.TEXT_SECONDARY);
        statusLabel.setName("student.org.status");
        heading.add(statusLabel, BorderLayout.CENTER);
        left.add(heading, BorderLayout.NORTH);

        tree.setName("student.org.tree");
        tree.getAccessibleContext().setAccessibleName("组织架构树");
        tree.setBackground(UiColors.BACKGROUND_PAGE);
        tree.setFont(UiTypography.BODY);
        tree.setCellRenderer(new OrgTreeCellRenderer());
        tree.addTreeSelectionListener(this::treeSelectionChanged);
        JScrollPane treeScroll = new JScrollPane(tree,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        treeScroll.setName("student.org.tree.scroll");
        treeScroll.setOpaque(false);
        treeScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        treeScroll.getViewport().setOpaque(false);
        treeScroll.getViewport().setBackground(UiColors.BACKGROUND_PAGE);
        left.add(treeScroll, BorderLayout.CENTER);

        JPanel treeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        treeButtons.setOpaque(false);
        addDeptButton.setName("student.org.add-dept");
        addDeptButton.setFont(UiTypography.BODY);
        addDeptButton.setEnabled(false);
        addDeptButton.addActionListener(e -> startAddDepartment());
        treeButtons.add(addDeptButton);
        addMajorButton.setName("student.org.add-major");
        addMajorButton.setFont(UiTypography.BODY);
        addMajorButton.setEnabled(false);
        addMajorButton.addActionListener(e -> startAddMajor());
        treeButtons.add(addMajorButton);
        addClassButton.setName("student.org.add-class");
        addClassButton.setFont(UiTypography.BODY);
        addClassButton.setEnabled(false);
        addClassButton.addActionListener(e -> startAddClass());
        treeButtons.add(addClassButton);
        addStudentButton.setName("student.org.add-student");
        addStudentButton.setFont(UiTypography.BODY);
        addStudentButton.setEnabled(false);
        addStudentButton.addActionListener(e -> startAddStudent());
        treeButtons.add(addStudentButton);
        left.add(treeButtons, BorderLayout.SOUTH);

        add(left, BorderLayout.WEST);

        editPanel.setOpaque(false);
        editPanel.setPreferredSize(new Dimension(360, 0));
        showPlaceholder();
        add(editPanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0));
        bottom.setOpaque(false);
        errorLabel.setFont(UiTypography.CAPTION);
        errorLabel.setForeground(UiColors.ERROR_FG);
        errorLabel.setName("student.org.error");
        bottom.add(errorLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    @Override public void addNotify() {
        super.addNotify();
        active = true;
        connectionChanged(connection.state());
        loadAll();
    }

    @Override public void removeNotify() {
        active = false;
        requestGeneration.incrementAndGet();
        super.removeNotify();
    }

    private void loadAll() { loadAll(null); }

    private void loadAll(Runnable onComplete) {
        long generation = requestGeneration.incrementAndGet();
        onEdt(() -> {
            if (active && generation == requestGeneration.get()) {
                statusLabel.setText("正在加载");
                errorLabel.setText(" ");
                addDeptButton.setEnabled(false);
            }
        });
        students.listDepartments(false).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure != null) {
                statusLabel.setText("加载失败");
                errorLabel.setText("组织架构加载失败，请稍后重试");
                updateAddButtons();
                return;
            }
            if (body == null || !body.success() || body.data() == null) {
                statusLabel.setText("加载失败");
                errorLabel.setText(safeMessage(body));
                updateAddButtons();
                return;
            }
            rootNode.removeAllChildren();
            treeModel.reload();
            ArrayList<DepartmentView> departments = body.data();
            for (DepartmentView dept : departments) {
                DefaultMutableTreeNode deptNode = new DefaultMutableTreeNode(dept, true);
                rootNode.add(deptNode);
                loadMajors(deptNode, dept.departmentId(), generation);
            }
            treeModel.reload();
            expandAll();
            statusLabel.setText(connection.state() == ConnectionState.CONNECTED ? "已加载" : "已断开连接");
            errorLabel.setText(" ");
            updateAddButtons();
            if (onComplete != null) onComplete.run();
        }));
    }

    private void loadMajors(DefaultMutableTreeNode deptNode, String departmentId, long generation) {
        students.listMajors(departmentId, false).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                for (MajorView major : body.data()) {
                    DefaultMutableTreeNode majorNode = new DefaultMutableTreeNode(major, true);
                    deptNode.add(majorNode);
                    loadClasses(majorNode, major.majorId(), generation);
                }
                treeModel.reload();
                expandAll();
            }
        }));
    }

    private void loadClasses(DefaultMutableTreeNode majorNode, String majorId, long generation) {
        students.listClasses(majorId, false).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                for (ClassView cls : body.data()) {
                    majorNode.add(new DefaultMutableTreeNode(cls, false));
                }
                treeModel.reload();
                expandAll();
            }
        }));
    }

    private void treeSelectionChanged(javax.swing.event.TreeSelectionEvent e) {
        TreePath path = e.getNewLeadSelectionPath();
        if (path == null) {
            selectedNode = null;
            editingTarget = null;
            isNewItem = false;
            showPlaceholder();
            updateAddButtons();
            return;
        }
        selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObject = selectedNode.getUserObject();
        if (userObject instanceof DepartmentView dept) {
            editingTarget = dept;
            isNewItem = false;
            showDepartmentForm(dept, false);
        } else if (userObject instanceof MajorView major) {
            editingTarget = major;
            isNewItem = false;
            showMajorForm(major, false);
        } else if (userObject instanceof ClassView cls) {
            editingTarget = cls;
            isNewItem = false;
            showClassForm(cls, false);
        } else {
            selectedNode = null;
            editingTarget = null;
            isNewItem = false;
            showPlaceholder();
        }
        updateAddButtons();
    }

    private void updateAddButtons() {
        boolean connected = connection.state() == ConnectionState.CONNECTED;
        boolean none = editingTarget == null;
        boolean isDept = editingTarget instanceof DepartmentView;
        boolean isMajor = editingTarget instanceof MajorView;
        boolean isClass = editingTarget instanceof ClassView;
        addDeptButton.setEnabled(connected && none);
        addMajorButton.setEnabled(connected && isDept);
        addClassButton.setEnabled(connected && isMajor);
        addStudentButton.setEnabled(connected && isClass);
    }

    private void showPlaceholder() {
        editPanel.removeAll();
        JLabel placeholder = new JLabel("请选择要编辑的项目");
        placeholder.setFont(UiTypography.SECTION_TITLE);
        placeholder.setForeground(UiColors.TEXT_SECONDARY);
        placeholder.setHorizontalAlignment(SwingConstants.CENTER);
        editPanel.add(placeholder, BorderLayout.CENTER);
        editPanel.revalidate();
        editPanel.repaint();
    }

    private void showDepartmentForm(DepartmentView dept, boolean isNew) {
        editPanel.removeAll();
        JPanel form = buildEditForm();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.WEST;

        JTextField codeField = editField("student.org.code", dept == null ? "" : dept.code());
        JTextField nameField = editField("student.org.name", dept == null ? "" : dept.name());
        JCheckBox activeBox = new JCheckBox("启用");
        activeBox.setName("student.org.active");
        activeBox.setFont(UiTypography.BODY);
        activeBox.setOpaque(false);
        activeBox.setSelected(dept == null || dept.active());

        addFormRow(form, c, "编号", codeField, 0);
        addFormRow(form, c, "名称", nameField, 1);
        c.gridy = 2; c.gridx = 0; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        form.add(activeBox, c);

        JButton saveButton = saveButton();
        saveButton.addActionListener(e -> saveDepartment(codeField, nameField, activeBox, dept, isNew));
        c.gridy = 3; c.gridx = 1; c.anchor = GridBagConstraints.EAST;
        form.add(saveButton, c);

        editPanel.add(form, BorderLayout.NORTH);
        editPanel.revalidate();
        editPanel.repaint();
    }

    private void showMajorForm(MajorView major, boolean isNew) {
        editPanel.removeAll();
        JPanel form = buildEditForm();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.WEST;

        String parentName = "";
        if (selectedNode != null) {
            Object nodeObj = selectedNode.getUserObject();
            if (nodeObj instanceof DepartmentView dept) {
                parentName = dept.code() + " - " + dept.name();
            } else if (selectedNode.getParent() != null) {
                Object parentObj = ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
                if (parentObj instanceof DepartmentView dept) parentName = dept.code() + " - " + dept.name();
            }
        }
        JLabel parentLabel = new JLabel(parentName);
        parentLabel.setName("student.org.parent");
        parentLabel.setFont(UiTypography.BODY);
        parentLabel.setForeground(UiColors.TEXT_SECONDARY);

        JTextField codeField = editField("student.org.code", major == null ? "" : major.code());
        JTextField nameField = editField("student.org.name", major == null ? "" : major.name());
        JCheckBox activeBox = new JCheckBox("启用");
        activeBox.setName("student.org.active");
        activeBox.setFont(UiTypography.BODY);
        activeBox.setOpaque(false);
        activeBox.setSelected(major == null || major.active());

        addFormRow(form, c, "所属院系", parentLabel, 0);
        addFormRow(form, c, "编号", codeField, 1);
        addFormRow(form, c, "名称", nameField, 2);
        c.gridy = 3; c.gridx = 0; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        form.add(activeBox, c);

        JButton saveButton = saveButton();
        saveButton.addActionListener(e -> saveMajor(codeField, nameField, activeBox, major, isNew));
        c.gridy = 4; c.gridx = 1; c.anchor = GridBagConstraints.EAST;
        form.add(saveButton, c);

        editPanel.add(form, BorderLayout.NORTH);
        editPanel.revalidate();
        editPanel.repaint();
    }

    private void showClassForm(ClassView cls, boolean isNew) {
        editPanel.removeAll();
        JPanel form = buildEditForm();
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.WEST;

        String parentName = "";
        if (selectedNode != null) {
            Object nodeObj = selectedNode.getUserObject();
            if (nodeObj instanceof MajorView major) {
                parentName = major.code() + " - " + major.name();
            } else if (selectedNode.getParent() != null) {
                Object parentObj = ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
                if (parentObj instanceof MajorView major) parentName = major.code() + " - " + major.name();
            }
        }
        JLabel parentLabel = new JLabel(parentName);
        parentLabel.setName("student.org.parent");
        parentLabel.setFont(UiTypography.BODY);
        parentLabel.setForeground(UiColors.TEXT_SECONDARY);

        JTextField codeField = editField("student.org.code", cls == null ? "" : cls.code());
        JTextField nameField = editField("student.org.name", cls == null ? "" : cls.name());
        JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(
                cls == null ? 2025 : cls.enrollmentYear(), 2000, 2099, 1));
        yearSpinner.setName("student.org.year");
        yearSpinner.setFont(UiTypography.BODY);
        yearSpinner.getAccessibleContext().setAccessibleName("入学年份");
        JSpinner numberSpinner = new JSpinner(new SpinnerNumberModel(
                cls == null ? 1 : cls.classNumber(), 1, 9, 1));
        numberSpinner.setName("student.org.number");
        numberSpinner.setFont(UiTypography.BODY);
        numberSpinner.getAccessibleContext().setAccessibleName("班级序号");
        JCheckBox activeBox = new JCheckBox("启用");
        activeBox.setName("student.org.active");
        activeBox.setFont(UiTypography.BODY);
        activeBox.setOpaque(false);
        activeBox.setSelected(cls == null || cls.active());

        addFormRow(form, c, "所属专业", parentLabel, 0);
        addFormRow(form, c, "编号", codeField, 1);
        addFormRow(form, c, "名称", nameField, 2);
        addFormRow(form, c, "入学年份", yearSpinner, 3);
        addFormRow(form, c, "班级序号", numberSpinner, 4);
        c.gridy = 5; c.gridx = 0; c.gridwidth = 2; c.fill = GridBagConstraints.NONE;
        form.add(activeBox, c);

        JButton saveButton = saveButton();
        saveButton.addActionListener(e -> saveClass(codeField, nameField, yearSpinner, numberSpinner, activeBox, cls, isNew));
        c.gridy = 6; c.gridx = 1; c.anchor = GridBagConstraints.EAST;
        form.add(saveButton, c);

        editPanel.add(form, BorderLayout.NORTH);
        editPanel.revalidate();
        editPanel.repaint();
    }

    private void saveDepartment(JTextField codeField, JTextField nameField, JCheckBox activeBox,
                                DepartmentView base, boolean isNew) {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        if (code.isEmpty()) { errorLabel.setText("编号不能为空"); return; }
        if (name.isEmpty()) { errorLabel.setText("名称不能为空"); return; }
        String id = isNew || base == null ? "" : base.departmentId();
        long version = isNew || base == null ? 0 : base.rowVersion();
        long generation = requestGeneration.incrementAndGet();
        errorLabel.setText(" ");
        students.saveDepartment(new SaveDepartmentCommand(id, code, name, activeBox.isSelected(), version))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (!active || generation != requestGeneration.get()) return;
                    if (failure != null) { errorLabel.setText("保存失败，请稍后重试"); return; }
                    if (body != null && body.success()) { loadAll(); return; }
                    if (body != null && "COMMON_CONCURRENT_MODIFICATION".equals(body.code())) {
                        loadAll(() -> errorLabel.setText("数据已被修改，请刷新后重试"));
                        return;
                    }
                    errorLabel.setText(safeMessage(body));
                }));
    }

    private void saveMajor(JTextField codeField, JTextField nameField, JCheckBox activeBox,
                           MajorView base, boolean isNew) {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        if (code.isEmpty()) { errorLabel.setText("编号不能为空"); return; }
        if (!MAJOR_CODE.matcher(code).matches()) { errorLabel.setText("专业编号必须为3位大写字母或数字"); return; }
        if (name.isEmpty()) { errorLabel.setText("名称不能为空"); return; }
        String departmentId = null;
        if (selectedNode != null) {
            Object nodeObj = selectedNode.getUserObject();
            if (nodeObj instanceof DepartmentView dept) {
                departmentId = dept.departmentId();
            } else if (selectedNode.getParent() != null) {
                Object parentObj = ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
                if (parentObj instanceof DepartmentView dept) departmentId = dept.departmentId();
            }
        }
        if (departmentId == null) { errorLabel.setText("请选择所属院系"); return; }
        String id = isNew || base == null ? "" : base.majorId();
        long version = isNew || base == null ? 0 : base.rowVersion();
        long generation = requestGeneration.incrementAndGet();
        errorLabel.setText(" ");
        students.saveMajor(new SaveMajorCommand(id, departmentId, code, name, activeBox.isSelected(), version))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (!active || generation != requestGeneration.get()) return;
                    if (failure != null) { errorLabel.setText("保存失败，请稍后重试"); return; }
                    if (body != null && body.success()) { loadAll(); return; }
                    if (body != null && "COMMON_CONCURRENT_MODIFICATION".equals(body.code())) {
                        loadAll(() -> errorLabel.setText("数据已被修改，请刷新后重试"));
                        return;
                    }
                    errorLabel.setText(safeMessage(body));
                }));
    }

    private void saveClass(JTextField codeField, JTextField nameField, JSpinner yearSpinner,
                           JSpinner numberSpinner, JCheckBox activeBox, ClassView base, boolean isNew) {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        int year = (Integer) yearSpinner.getValue();
        int number = (Integer) numberSpinner.getValue();
        if (code.isEmpty()) { errorLabel.setText("编号不能为空"); return; }
        if (name.isEmpty()) { errorLabel.setText("名称不能为空"); return; }
        if (year < 2000 || year > 2099) { errorLabel.setText("入学年份必须在2000-2099之间"); return; }
        if (number < 1 || number > 9) { errorLabel.setText("班级序号必须在1-9之间"); return; }
        String majorId = null;
        if (selectedNode != null) {
            Object nodeObj = selectedNode.getUserObject();
            if (nodeObj instanceof MajorView major) {
                majorId = major.majorId();
            } else if (selectedNode.getParent() != null) {
                Object parentObj = ((DefaultMutableTreeNode) selectedNode.getParent()).getUserObject();
                if (parentObj instanceof MajorView major) majorId = major.majorId();
            }
        }
        if (majorId == null) { errorLabel.setText("请选择所属专业"); return; }
        String id = isNew || base == null ? "" : base.classId();
        long version = isNew || base == null ? 0 : base.rowVersion();
        long generation = requestGeneration.incrementAndGet();
        errorLabel.setText(" ");
        students.saveClass(new SaveClassCommand(id, majorId, code, name, year, number, activeBox.isSelected(), version))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (!active || generation != requestGeneration.get()) return;
                    if (failure != null) { errorLabel.setText("保存失败，请稍后重试"); return; }
                    if (body != null && body.success()) { loadAll(); return; }
                    if (body != null && "COMMON_CONCURRENT_MODIFICATION".equals(body.code())) {
                        loadAll(() -> errorLabel.setText("数据已被修改，请刷新后重试"));
                        return;
                    }
                    errorLabel.setText(safeMessage(body));
                }));
    }

    private void startAddDepartment() {
        selectedNode = null;
        editingTarget = null;
        isNewItem = true;
        tree.clearSelection();
        showDepartmentForm(null, true);
        updateAddButtons();
    }

    private void startAddMajor() {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof DepartmentView)) return;
        isNewItem = true;
        showMajorForm(null, true);
    }

    private void startAddClass() {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof MajorView)) return;
        isNewItem = true;
        showClassForm(null, true);
    }

    private void startAddStudent() {
        if (selectedNode == null || !(selectedNode.getUserObject() instanceof ClassView cls)) return;
        DefaultMutableTreeNode majorNode = (DefaultMutableTreeNode) selectedNode.getParent();
        DefaultMutableTreeNode departmentNode = majorNode == null ? null
                : (DefaultMutableTreeNode) majorNode.getParent();
        if (majorNode == null || departmentNode == null
                || !(majorNode.getUserObject() instanceof MajorView major)
                || !(departmentNode.getUserObject() instanceof DepartmentView department)) return;
        new ManualStudentCreationDialog(SwingUtilities.getWindowAncestor(this), students,
                department, major, cls).setVisible(true);
    }

    private void connectionChanged(ConnectionState state) {
        onEdt(() -> {
            if (!active) return;
            updateAddButtons();
        });
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private JPanel buildEditForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        return form;
    }

    private void addFormRow(JPanel form, GridBagConstraints c, String label, Component field, int row) {
        c.gridy = row; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.gridwidth = 1;
        JLabel l = new JLabel(label);
        l.setFont(UiTypography.CAPTION);
        l.setForeground(UiColors.TEXT_SECONDARY);
        form.add(l, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, c);
    }

    private JTextField editField(String name, String value) {
        JTextField field = new JTextField(value, 20);
        field.setName(name);
        field.setFont(UiTypography.BODY);
        field.setBorder(UiBorders.LINE);
        return field;
    }

    private JButton saveButton() {
        JButton button = new JButton("保存");
        button.setName("student.org.save");
        button.setFont(UiTypography.BODY);
        button.getAccessibleContext().setAccessibleName("保存");
        return button;
    }

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    private static String safeMessage(ResponseBody<?> body) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : "操作失败，请稍后重试";
    }

    private static final class OrgTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node) {
                Object obj = node.getUserObject();
                if (obj instanceof DepartmentView dept) setText(dept.code() + " - " + dept.name());
                else if (obj instanceof MajorView major) setText(major.code() + " - " + major.name());
                else if (obj instanceof ClassView cls) setText(cls.code() + " - " + cls.name());
            }
            if (selected) {
                setBackgroundSelectionColor(UiColors.PRIMARY);
                setForeground(UiColors.TEXT_ON_PRIMARY);
            } else {
                setBackgroundNonSelectionColor(UiColors.BACKGROUND_PAGE);
                setForeground(UiColors.TEXT_PRIMARY);
            }
            setBackgroundSelectionColor(UiColors.PRIMARY);
            setBackgroundNonSelectionColor(UiColors.BACKGROUND_PAGE);
            return this;
        }
    }
}
