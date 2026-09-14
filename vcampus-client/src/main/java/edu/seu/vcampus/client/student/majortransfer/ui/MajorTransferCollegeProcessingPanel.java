package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Processes transfer options and related applications for one college. */
public final class MajorTransferCollegeProcessingPanel extends JPanel {
    private final StudentClientService students;
    private final JComboBox<MajorTransferBatchView> batches = new JComboBox<>();
    private final DefaultListModel<MajorTransferApplicationView> model = new DefaultListModel<>();
    private final JList<MajorTransferApplicationView> applications = new JList<>(model);
    private final JTextArea detail = new JTextArea();
    private final JPanel attachments = new JPanel();
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JLabel status = new JLabel(" ");
    private final Set<String> ownedOptions = new HashSet<>();
    private final MajorTransferCollegeActions collegeActions;

    /** Creates the college-scoped transfer workspace. */
    public MajorTransferCollegeProcessingPanel(StudentClientService students) {
        super(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        this.students = Objects.requireNonNull(students);
        collegeActions = new MajorTransferCollegeActions(this, students, actions, status,
                this::loadDetail, this::loadSelectedBatch);
        setName("major-transfer.college-processing");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(new EmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_2,
                UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        build();
    }

    private void build() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setOpaque(false);
        JButton option = new JButton("维护本学院招生专业");
        option.setName("saveOptionButton");
        option.addActionListener(event -> {
            MajorTransferBatchView batch = (MajorTransferBatchView) batches.getSelectedItem();
            if (batch != null) MajorTransferCollegeDialogs.addOption(
                    this, students, batch, this::loadSelectedBatch);
        });
        JButton refresh = new JButton("刷新");
        refresh.addActionListener(event -> refresh());
        toolbar.add(new JLabel("批次："));
        toolbar.add(batches);
        toolbar.add(option);
        toolbar.add(refresh);
        batches.addActionListener(event -> loadSelectedBatch());

        applications.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        applications.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof MajorTransferApplicationView app) {
                    setText(app.studentName() + "  " + app.fromMajorName() + " → "
                            + app.targetMajorName() + "  [" + app.status() + "]");
                }
                return this;
            }
        });
        applications.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) loadDetail();
        });
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        attachments.setLayout(new BoxLayout(attachments, BoxLayout.Y_AXIS));
        JPanel detailArea = new JPanel(new BorderLayout());
        detailArea.add(new JScrollPane(detail), BorderLayout.CENTER);
        detailArea.add(attachments, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(applications), detailArea);
        split.setResizeWeight(0.4);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(actions, BorderLayout.CENTER);
        bottom.add(status, BorderLayout.SOUTH);
        add(toolbar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    @Override public void addNotify() {
        super.addNotify();
        refresh();
    }

    private void refresh() {
        students.listTransferBatches().whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success()) {
                        status.setText(message(response, "批次加载失败"));
                        return;
                    }
                    batches.removeAllItems();
                    response.data().forEach(batches::addItem);
                    if (batches.getItemCount() > 0) batches.setSelectedIndex(0);
                }));
    }

    private void loadSelectedBatch() {
        MajorTransferBatchView batch = (MajorTransferBatchView) batches.getSelectedItem();
        model.clear();
        ownedOptions.clear();
        if (batch == null) return;
        students.listTransferOptions(batch.batchId()).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        response.data().forEach(option -> ownedOptions.add(option.optionId()));
                    }
                    loadApplications(batch.batchId());
                }));
    }

    private void loadApplications(String batchId) {
        students.listTransferApplications(new MajorTransferApplicationQuery(batchId, null, null))
                .whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
                    model.clear();
                    if (response != null && response.success()) response.data().forEach(model::addElement);
                    else status.setText(message(response, "申请加载失败"));
                }));
    }

    private void loadDetail() {
        MajorTransferApplicationView selected = applications.getSelectedValue();
        if (selected == null) return;
        students.getTransferApplication(selected.applicationId()).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) renderDetail(response.data());
                    else status.setText(message(response, "申请加载失败"));
                }));
    }

    void renderDetail(MajorTransferApplicationView app) {
        detail.setText("学生：" + app.studentName() + "（" + app.fromStudentNumber() + "）\n"
                + "原学院/专业：" + app.fromDepartmentName() + " / " + app.fromMajorName() + "\n"
                + "目标学院/专业：" + app.targetDepartmentName() + " / " + app.targetMajorName() + "\n"
                + "状态：" + app.status() + "\n申请理由：" + app.reason());
        MajorTransferAttachmentDownloader.render(
                this, students, attachments, status, app.attachments());
        collegeActions.render(app, ownedOptions.contains(app.optionId()));
    }

    private static String message(edu.seu.vcampus.common.protocol.ResponseBody<?> response,
            String fallback) {
        return response == null || response.message() == null ? fallback : response.message();
    }
}
