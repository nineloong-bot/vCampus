package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;

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
    private final JLabel readiness = new JLabel("终审状态：未加载");
    private final JButton finalizeBatch = new JButton("批次终审并生效");
    private final Set<String> ownedOptions = new HashSet<>();
    private final MajorTransferCollegeActions collegeActions;
    private EmbeddedEditorHost editorHost;
    private long batchRequest;
    private long detailRequest;

    /** Creates the college-scoped transfer workspace. */
    public MajorTransferCollegeProcessingPanel(StudentClientService students) {
        super(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        this.students = Objects.requireNonNull(students);
        collegeActions = new MajorTransferCollegeActions(this, students, actions, status,
                this::loadDetail, this::loadSelectedBatch, this::openEditor);
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
            if (batch != null) openEditor((complete, cancel) -> new MajorTransferOptionEditorPanel(
                    students, batch, () -> { complete.run(); loadSelectedBatch(); }, cancel));
        });
        JButton refresh = new JButton("刷新");
        refresh.addActionListener(event -> refresh());
        toolbar.add(new JLabel("批次："));
        batches.setRenderer(new MajorTransferBatchChoiceRenderer());
        batches.setPreferredSize(new Dimension(360, batches.getPreferredSize().height));
        toolbar.add(batches);
        toolbar.add(option);
        toolbar.add(refresh);
        finalizeBatch.setName("major-transfer.finalize-batch");
        finalizeBatch.setEnabled(false);
        finalizeBatch.addActionListener(event -> finalizeSelectedBatch());
        toolbar.add(finalizeBatch);
        toolbar.add(readiness);
        batches.addActionListener(event -> loadSelectedBatch());

        applications.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        applications.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof MajorTransferApplicationView app) {
                    setText(app.studentName() + "  " + app.fromMajorName() + " → "
                            + app.targetMajorName() + "  [" + MajorTransferStatusText.status(app.status()) + "]");
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
        MajorTransferCollegeWorkspaceView split = new MajorTransferCollegeWorkspaceView(
                applications, detail, attachments);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(actions, BorderLayout.CENTER);
        bottom.add(status, BorderLayout.SOUTH);
        JPanel list = new JPanel(new BorderLayout());
        list.add(toolbar, BorderLayout.NORTH);
        list.add(split, BorderLayout.CENTER);
        list.add(bottom, BorderLayout.SOUTH);
        editorHost = new EmbeddedEditorHost(list);
        add(editorHost, BorderLayout.CENTER);
    }

    private void openEditor(BiFunction<Runnable, Runnable, EmbeddedEditor> factory) {
        editorHost.showEditor(factory);
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
        long request = ++batchRequest;
        model.clear();
        ownedOptions.clear();
        detail.setText("请选择一条转专业申请查看详情");
        actions.removeAll();
        finalizeBatch.setEnabled(false);
        readiness.setText("终审状态：加载中…");
        if (batch == null) return;
        students.listTransferOptions(batch.batchId()).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (request != batchRequest) return;
                    if (response != null && response.success()) {
                        response.data().forEach(option -> ownedOptions.add(option.optionId()));
                    }
                    loadApplications(batch.batchId());
                    loadReadiness(batch.batchId(), request);
                }));
    }

    private void loadReadiness(String batchId, long request) {
        students.getTransferBatchReadiness(batchId).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (request != batchRequest) return;
                    if (response == null || !response.success()) {
                        readiness.setText("终审状态：" + message(response, "加载失败"));
                        finalizeBatch.setEnabled(false);
                        return;
                    }
                    MajorTransferBatchReadinessView value = response.data();
                    readiness.setText("拟录取 " + value.assessed() + " / 驳回 " + value.rejected()
                            + " / 取消 " + value.cancelled() + " / 未处理 " + value.unresolved()
                            + (value.ready() ? "" : " — " + value.reason()));
                    finalizeBatch.putClientProperty("batchVersion", value.batchVersion());
                    finalizeBatch.setEnabled(value.ready());
                }));
    }

    private void finalizeSelectedBatch() {
        MajorTransferBatchView batch = (MajorTransferBatchView) batches.getSelectedItem();
        Object version = finalizeBatch.getClientProperty("batchVersion");
        if (batch == null || !(version instanceof Long expectedVersion)) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "将一次性生效本批次全部拟录取学生，并自动分班、换学号和清理选课。是否继续？",
                "确认批次终审", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        finalizeBatch.setEnabled(false);
        students.finalizeTransferBatch(new FinalizeMajorTransferBatchCommand(
                batch.batchId(), expectedVersion)).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        status.setText("已生效 " + response.data().effectiveStudents()
                                + " 名学生，自动退选 "
                                + response.data().droppedEnrollments() + " 条课程");
                        refresh();
                    } else {
                        status.setText(message(response, "批次终审失败"));
                        loadReadiness(batch.batchId(), batchRequest);
                    }
                }));
    }

    private void loadApplications(String batchId) {
        long request = batchRequest;
        students.listTransferApplications(new MajorTransferApplicationQuery(batchId, null, null))
                .whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
                    if (request != batchRequest) return;
                    model.clear();
                    if (response != null && response.success()) response.data().forEach(model::addElement);
                    else status.setText(message(response, "申请加载失败"));
                }));
    }

    private void loadDetail() {
        MajorTransferApplicationView selected = applications.getSelectedValue();
        if (selected == null) return;
        long request = ++detailRequest;
        students.getTransferApplication(selected.applicationId()).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (request != detailRequest) return;
                    if (response != null && response.success()) renderDetail(response.data());
                    else status.setText(message(response, "申请加载失败"));
                }));
    }

    void renderDetail(MajorTransferApplicationView app) {
        detail.setText("学生：" + app.studentName() + "（" + app.fromStudentNumber() + "）\n"
                + "原学院/专业：" + app.fromDepartmentName() + " / " + app.fromMajorName() + "\n"
                + "目标学院/专业：" + app.targetDepartmentName() + " / " + app.targetMajorName() + "\n"
                + "状态：" + MajorTransferStatusText.status(app.status()) + "\n申请理由：" + app.reason());
        MajorTransferAttachmentDownloader.render(
                this, students, attachments, status, app.attachments());
        collegeActions.render(app, ownedOptions.contains(app.optionId()));
    }

    private static String message(edu.seu.vcampus.common.protocol.ResponseBody<?> response,
            String fallback) {
        return response == null || response.message() == null ? fallback : response.message();
    }
}
