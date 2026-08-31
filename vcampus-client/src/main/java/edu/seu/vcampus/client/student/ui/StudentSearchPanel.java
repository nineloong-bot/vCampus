package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class StudentSearchPanel extends JPanel {
    private final StudentClientService students;
    private final ClientConnection connection;
    private final Consumer<String> viewStudent;
    private final AtomicLong requestGeneration = new AtomicLong();
    private final java.util.List<StudentSummary> currentResults = new ArrayList<>();
    private volatile boolean active;
    private boolean suppressComboEvents;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 20;

    private JTextField keywordField;
    private JComboBox<Object> departmentCombo;
    private JComboBox<Object> majorCombo;
    private JComboBox<Object> classCombo;
    private JComboBox<Object> statusCombo;
    private JButton searchButton;
    private JTable resultsTable;
    private DefaultTableModel tableModel;
    private JLabel emptyLabel;
    private JLabel pageInfoLabel;
    private JButton prevButton;
    private JButton nextButton;
    private JLabel statusLabel;
    private JLabel errorLabel;

    public StudentSearchPanel(StudentClientService students, ClientConnection connection,
                              Consumer<String> viewStudent) {
        super(new BorderLayout(0, UiSpacing.SPACE_3));
        this.students = Objects.requireNonNull(students, "students");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.viewStudent = Objects.requireNonNull(viewStudent, "viewStudent");
        setName("student.search");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        buildPage();
        connection.addStateListener(this::connectionChanged);
    }

    private void buildPage() {
        JPanel heading = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        heading.setOpaque(false);
        JLabel title = new JLabel("学生查询");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        title.setName("student.search.title");
        heading.add(title, BorderLayout.NORTH);
        statusLabel = new JLabel("就绪");
        statusLabel.setFont(UiTypography.CAPTION);
        statusLabel.setForeground(UiColors.TEXT_SECONDARY);
        statusLabel.setName("student.search.status");
        heading.add(statusLabel, BorderLayout.SOUTH);
        JPanel filterBar = buildFilterBar();
        heading.add(filterBar, BorderLayout.CENTER);
        add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        center.setOpaque(false);
        resultsTable = new JTable();
        resultsTable.setName("student.search.table");
        resultsTable.setModel(new DefaultTableModel(new String[]{"一卡通号", "学号", "姓名", "状态"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        tableModel = (DefaultTableModel) resultsTable.getModel();
        resultsTable.setRowHeight(UiSpacing.SPACE_6);
        resultsTable.setFont(UiTypography.BODY);
        resultsTable.getTableHeader().setFont(UiTypography.SECTION_TITLE);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(new EmptyBorder(UiSpacing.SPACE_1, UiSpacing.SPACE_2, UiSpacing.SPACE_1, UiSpacing.SPACE_2));
                if (!isSelected) setBackground(row % 2 == 0 ? UiColors.BACKGROUND_PAGE : UiColors.BACKGROUND_SUBTLE);
                return this;
            }
        });
        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openSelectedStudent();
            }
        });
        JScrollPane scrollPane = new JScrollPane(resultsTable,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setName("student.search.table.scroll");
        scrollPane.setBorder(UiBorders.LINE);
        scrollPane.getViewport().setBackground(UiColors.BACKGROUND_PAGE);
        emptyLabel = new JLabel("输入关键词并点击搜索", SwingConstants.CENTER);
        emptyLabel.setFont(UiTypography.SECTION_TITLE);
        emptyLabel.setForeground(UiColors.TEXT_SECONDARY);
        emptyLabel.setName("student.search.empty");
        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.setOpaque(false);
        tableArea.add(scrollPane, BorderLayout.CENTER);
        tableArea.add(emptyLabel, BorderLayout.SOUTH);
        emptyLabel.setVisible(true);
        scrollPane.setVisible(false);

        JPanel paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, UiSpacing.SPACE_3, UiSpacing.SPACE_2));
        paginationPanel.setOpaque(false);
        prevButton = new JButton("上一页");
        prevButton.setName("student.search.prev");
        prevButton.setFont(UiTypography.BODY);
        prevButton.setEnabled(false);
        prevButton.addActionListener(e -> changePage(-1));
        pageInfoLabel = new JLabel("第0页/共0条");
        pageInfoLabel.setFont(UiTypography.BODY);
        pageInfoLabel.setForeground(UiColors.TEXT_PRIMARY);
        pageInfoLabel.setName("student.search.page");
        nextButton = new JButton("下一页");
        nextButton.setName("student.search.next");
        nextButton.setFont(UiTypography.BODY);
        nextButton.setEnabled(false);
        nextButton.addActionListener(e -> changePage(1));
        paginationPanel.add(prevButton);
        paginationPanel.add(pageInfoLabel);
        paginationPanel.add(nextButton);
        center.add(tableArea, BorderLayout.CENTER);
        center.add(paginationPanel, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0));
        bottom.setOpaque(false);
        errorLabel = new JLabel(" ");
        errorLabel.setFont(UiTypography.CAPTION);
        errorLabel.setForeground(UiColors.ERROR_FG);
        errorLabel.setName("student.search.error");
        bottom.add(errorLabel, BorderLayout.WEST);
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, UiSpacing.SPACE_1));
        bar.setOpaque(false);
        bar.setName("student.search.filters");
        keywordField = new JTextField(12);
        keywordField.setName("student.search.keyword");
        keywordField.setFont(UiTypography.BODY);
        keywordField.setBorder(UiBorders.LINE);
        keywordField.getAccessibleContext().setAccessibleName("搜索关键词");
        keywordField.addActionListener(e -> search());
        departmentCombo = new JComboBox<>();
        departmentCombo.setName("student.search.department");
        departmentCombo.setFont(UiTypography.BODY);
        departmentCombo.getAccessibleContext().setAccessibleName("院系筛选");
        departmentCombo.addItem("全部");
        departmentCombo.addActionListener(e -> {
            if (suppressComboEvents) return;
            Object item = departmentCombo.getSelectedItem();
            String id = (item instanceof DepartmentView d) ? d.departmentId() : null;
            long generation = requestGeneration.incrementAndGet();
            cascadeLoadMajors(id, generation);
            executeSearch(generation);
        });
        majorCombo = new JComboBox<>();
        majorCombo.setName("student.search.major");
        majorCombo.setFont(UiTypography.BODY);
        majorCombo.getAccessibleContext().setAccessibleName("专业筛选");
        majorCombo.addItem("全部");
        majorCombo.addActionListener(e -> {
            if (suppressComboEvents) return;
            Object item = majorCombo.getSelectedItem();
            String id = (item instanceof MajorView m) ? m.majorId() : null;
            long generation = requestGeneration.incrementAndGet();
            cascadeLoadClasses(id, generation);
            executeSearch(generation);
        });
        classCombo = new JComboBox<>();
        classCombo.setName("student.search.class");
        classCombo.setFont(UiTypography.BODY);
        classCombo.getAccessibleContext().setAccessibleName("班级筛选");
        classCombo.addItem("全部");
        statusCombo = new JComboBox<>(new String[]{"全部", "正常", "休学", "已毕业", "已退学"});
        statusCombo.setName("student.search.status");
        statusCombo.setFont(UiTypography.BODY);
        statusCombo.getAccessibleContext().setAccessibleName("状态筛选");
        statusCombo.addActionListener(e -> search());
        searchButton = new JButton("搜索");
        searchButton.setName("student.search.submit");
        searchButton.setFont(UiTypography.BODY);
        searchButton.setBackground(UiColors.ACCENT);
        searchButton.setForeground(UiColors.TEXT_ON_PRIMARY);
        searchButton.setBorder(BorderFactory.createCompoundBorder(UiBorders.LINE,
                BorderFactory.createEmptyBorder(UiSpacing.SPACE_1, UiSpacing.SPACE_3,
                        UiSpacing.SPACE_1, UiSpacing.SPACE_3)));
        searchButton.getAccessibleContext().setAccessibleName("搜索");
        searchButton.addActionListener(e -> search());
        JLabel keywordLabel = new JLabel("关键词");
        keywordLabel.setFont(UiTypography.CAPTION);
        keywordLabel.setForeground(UiColors.TEXT_SECONDARY);
        bar.add(keywordLabel);
        bar.add(keywordField);
        JLabel deptLabel = new JLabel("  院系:");
        deptLabel.setFont(UiTypography.CAPTION);
        bar.add(deptLabel);
        bar.add(departmentCombo);
        JLabel majorLabel = new JLabel("  专业:");
        majorLabel.setFont(UiTypography.CAPTION);
        bar.add(majorLabel);
        bar.add(majorCombo);
        JLabel classLabel = new JLabel("  班级:");
        classLabel.setFont(UiTypography.CAPTION);
        bar.add(classLabel);
        bar.add(classCombo);
        JLabel statusLabel = new JLabel("  状态:");
        statusLabel.setFont(UiTypography.CAPTION);
        bar.add(statusLabel);
        bar.add(statusCombo);
        bar.add(searchButton);
        return bar;
    }

    @Override public void addNotify() {
        super.addNotify();
        active = true;
        connectionChanged(connection.state());
        long generation = requestGeneration.incrementAndGet();
        loadDepartments(generation);
        executeSearch(generation);
    }

    @Override public void removeNotify() {
        active = false;
        requestGeneration.incrementAndGet();
        super.removeNotify();
    }

    public void search() {
        long generation = requestGeneration.incrementAndGet();
        executeSearch(generation);
    }

    private void executeSearch(long generation) {
        String keyword = keywordField.getText().trim();
        String departmentId = getSelectedId(departmentCombo, DepartmentView.class);
        String majorId = getSelectedId(majorCombo, MajorView.class);
        String classId = getSelectedId(classCombo, ClassView.class);
        StudentStatus status = getSelectedStatus();
        if (currentPage < 1) currentPage = 1;
        onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            setSearching(true);
        });
        students.search(new StudentSearchQuery(keyword, departmentId, majorId, classId, status, currentPage, PAGE_SIZE))
                .whenComplete((body, failure) -> onEdt(() -> {
                    if (!active || generation != requestGeneration.get()) return;
                    setSearching(false);
                    if (failure != null) {
                        renderError("搜索失败，请稍后重试");
                    } else if (body != null && body.success() && body.data() != null) {
                        renderResults(body.data());
                    } else {
                        renderError(safeMessage(body));
                    }
                }));
    }

    private void changePage(int delta) {
        int next = currentPage + delta;
        if (next < 1) return;
        currentPage = next;
        search();
    }

    private void loadDepartments(long generation) {
        students.listDepartments(true).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<DepartmentView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    departmentCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                } finally {
                    suppressComboEvents = false;
                }
            } else {
                suppressComboEvents = true;
                try {
                    departmentCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
                } finally {
                    suppressComboEvents = false;
                }
            }
        }));
    }

    private void cascadeLoadMajors(String departmentId, long generation) {
        suppressComboEvents = true;
        try {
            majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"正在加载..."}));
            classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
        } finally {
            suppressComboEvents = false;
        }
        if (departmentId == null) {
            suppressComboEvents = true;
            try {
                majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
            } finally {
                suppressComboEvents = false;
            }
            return;
        }
        students.listMajors(departmentId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<MajorView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    majorCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                } finally {
                    suppressComboEvents = false;
                }
            } else {
                suppressComboEvents = true;
                try {
                    majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
                } finally {
                    suppressComboEvents = false;
                }
            }
        }));
    }

    private void cascadeLoadClasses(String majorId, long generation) {
        suppressComboEvents = true;
        try {
            classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"正在加载..."}));
        } finally {
            suppressComboEvents = false;
        }
        if (majorId == null) {
            suppressComboEvents = true;
            try {
                classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
            } finally {
                suppressComboEvents = false;
            }
            return;
        }
        students.listClasses(majorId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<ClassView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    classCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                } finally {
                    suppressComboEvents = false;
                }
            } else {
                suppressComboEvents = true;
                try {
                    classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
                } finally {
                    suppressComboEvents = false;
                }
            }
        }));
    }

    private void renderResults(PageResult<StudentSummary> page) {
        tableModel.setRowCount(0);
        currentResults.clear();
        for (StudentSummary s : page.items()) {
            tableModel.addRow(new Object[]{s.campusCardNumber(), s.studentNumber(), s.studentName(), statusText(s.status())});
        }
        currentResults.addAll(page.items());
        if (page.items().isEmpty()) {
            resultsTable.setVisible(false);
            emptyLabel.setText("未找到匹配的学生");
            emptyLabel.setVisible(true);
        } else {
            resultsTable.setVisible(true);
            emptyLabel.setVisible(false);
        }
        long total = page.total();
        pageInfoLabel.setText("第" + page.page() + "页/共" + total + "条");
        prevButton.setEnabled(page.page() > 1 && connection.state() == ConnectionState.CONNECTED);
        nextButton.setEnabled((long) page.page() * page.pageSize() < total && connection.state() == ConnectionState.CONNECTED);
        statusLabel.setText("搜索完成");
        errorLabel.setText(" ");
    }

    private void renderError(String message) {
        tableModel.setRowCount(0);
        currentResults.clear();
        resultsTable.setVisible(false);
        emptyLabel.setText("搜索出错");
        emptyLabel.setVisible(true);
        pageInfoLabel.setText("第0页/共0条");
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        statusLabel.setText("搜索失败");
        errorLabel.setText(message);
    }

    private void setSearching(boolean searching) {
        searchButton.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        departmentCombo.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        majorCombo.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        classCombo.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        statusCombo.setEnabled(!searching && connection.state() == ConnectionState.CONNECTED);
        if (searching) {
            searchButton.setText("搜索中...");
            statusLabel.setText("正在搜索...");
        } else {
            searchButton.setText("搜索");
        }
    }

    private void connectionChanged(ConnectionState state) {
        onEdt(() -> {
            if (!active) return;
            boolean connected = state == ConnectionState.CONNECTED;
            searchButton.setEnabled(connected);
            departmentCombo.setEnabled(connected);
            majorCombo.setEnabled(connected);
            classCombo.setEnabled(connected);
            statusCombo.setEnabled(connected);
            statusLabel.setText(connected ? "就绪" : "连接已断开");
        });
    }

    private void openSelectedStudent() {
        int row = resultsTable.getSelectedRow();
        if (row < 0 || row >= currentResults.size()) return;
        viewStudent.accept(currentResults.get(row).studentId());
    }

    private String getSelectedId(JComboBox<Object> combo, Class<?> type) {
        Object item = combo.getSelectedItem();
        if (item == null || item.equals("全部") || item.equals("正在加载...")) return null;
        if (type == DepartmentView.class && item instanceof DepartmentView d) return d.departmentId();
        if (type == MajorView.class && item instanceof MajorView m) return m.majorId();
        if (type == ClassView.class && item instanceof ClassView c) return c.classId();
        return null;
    }

    private StudentStatus getSelectedStatus() {
        Object item = statusCombo.getSelectedItem();
        if (item == null || item.equals("全部")) return null;
        return switch ((String) item) {
            case "正常" -> StudentStatus.ACTIVE;
            case "休学" -> StudentStatus.SUSPENDED;
            case "已毕业" -> StudentStatus.GRADUATED;
            case "已退学" -> StudentStatus.WITHDRAWN;
            default -> null;
        };
    }

    private static String statusText(StudentStatus status) {
        if (status == null) return "";
        return switch (status) {
            case ACTIVE -> "正常";
            case SUSPENDED -> "休学";
            case GRADUATED -> "已毕业";
            case WITHDRAWN -> "已退学";
        };
    }

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    private static String safeMessage(ResponseBody<?> body) {
        return body != null && body.message() != null && !body.message().isBlank() ? body.message() : "搜索失败，请稍后重试";
    }
}
