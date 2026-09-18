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
    private final JComboBox<MajorTransferOptionView> finalizationOptions = new JComboBox<>();
    private final DefaultListModel<MajorTransferApplicationView> model = new DefaultListModel<>();
    private final JList<MajorTransferApplicationView> applications = new JList<>(model);
    private final JPanel attachments = new JPanel();
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JLabel status = new JLabel(" ");
    private final JLabel readiness = new JLabel("终审状态：未加载");
    private final JButton finalizeOption = new JButton("专业终审");
    private final JButton effectiveOption = new JButton("专业生效");
    private final JButton rollbackOption = new JButton("回退专业终审");
    private final Set<String> ownedOptions = new HashSet<>();
    private final MajorTransferCollegeActions collegeActions;
    private final MajorTransferCollegeOptionFinalizer optionFinalizer;
    private MajorTransferCollegeWorkspaceView workspaceView;
    private EmbeddedEditorHost editorHost;
    private long batchRequest;
    private long readinessRequest;
    private long detailRequest;

    /** Creates the college-scoped transfer workspace. */
    public MajorTransferCollegeProcessingPanel(StudentClientService students) {
        super(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        this.students = Objects.requireNonNull(students);
        optionFinalizer = new MajorTransferCollegeOptionFinalizer(this, students, readiness,
                finalizeOption, effectiveOption, rollbackOption, status,
                () -> (MajorTransferOptionView) finalizationOptions.getSelectedItem(),
                () -> readinessRequest, this::loadSelectedBatch);
        collegeActions = new MajorTransferCollegeActions(this, students, actions, status,
                this::loadDetail, this::refreshReadiness, this::openEditor);
        setName("major-transfer.college-processing");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(new EmptyBorder(UiSpacing.SPACE_2, UiSpacing.SPACE_2, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        build();
    }

    private void build() {
        JButton option = new JButton("维护本学院招生专业");
        option.setName("saveOptionButton");
        option.addActionListener(event -> {
            MajorTransferBatchView batch = (MajorTransferBatchView) batches.getSelectedItem();
            if (batch != null) openEditor((complete, cancel) -> new MajorTransferOptionEditorPanel(
                    students, batch, () -> { complete.run(); loadSelectedBatch(); }, cancel));
        });
        JButton refresh = new JButton("刷新");
        refresh.setName("major-transfer.college-refresh");
        refresh.getAccessibleContext().setAccessibleName("刷新转专业批次及终审状态");
        refresh.addActionListener(event -> refresh());
        batches.setRenderer(new MajorTransferBatchChoiceRenderer());
        batches.setPreferredSize(new Dimension(360, batches.getPreferredSize().height));
        finalizationOptions.setName("major-transfer.finalization-option");
        finalizationOptions.setPreferredSize(new Dimension(220,
                finalizationOptions.getPreferredSize().height));
        finalizationOptions.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof MajorTransferOptionView optionView) {
                    setText(optionView.targetMajorName() + "（名额 "
                            + optionView.receiveQuota() + "）");
                }
                return this;
            }
        });
        finalizationOptions.addActionListener(event -> loadSelectedOptionReadiness());
        finalizeOption.setName("major-transfer.finalize-option");
        finalizeOption.setEnabled(false);
        finalizeOption.addActionListener(event -> optionFinalizer.finalizeSelectedOption());
        effectiveOption.setName("major-transfer.effective-option");
        effectiveOption.setEnabled(false);
        effectiveOption.addActionListener(event -> optionFinalizer.effectiveSelectedOption());
        rollbackOption.setName("major-transfer.rollback-option");
        rollbackOption.setEnabled(false);
        rollbackOption.addActionListener(event -> optionFinalizer.rollbackSelectedOption());
        batches.addActionListener(event -> loadSelectedBatch());
        JPanel toolbar = new MajorTransferCollegeToolbar(batches, option, refresh,
                finalizationOptions, finalizeOption, effectiveOption, rollbackOption, readiness);

        applications.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        applications.setCellRenderer(new MajorTransferApplicationListRenderer());
        applications.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) loadDetail();
        });
        attachments.setLayout(new BoxLayout(attachments, BoxLayout.Y_AXIS));
        workspaceView = new MajorTransferCollegeWorkspaceView(applications, attachments);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(actions, BorderLayout.CENTER);
        bottom.add(status, BorderLayout.SOUTH);
        JPanel list = new JPanel(new BorderLayout());
        list.add(toolbar, BorderLayout.NORTH);
        list.add(workspaceView, BorderLayout.CENTER);
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
        finalizationOptions.removeAllItems();
        readinessRequest++;
        if (workspaceView != null) workspaceView.clearDetail();
        actions.removeAll();
        setFinalizationActions(false);
        readiness.setText("终审状态：加载中…");
        if (batch == null) return;
        students.listTransferOptions(batch.batchId()).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (request != batchRequest) return;
                    if (response != null && response.success()) {
                        response.data().forEach(option -> {
                            ownedOptions.add(option.optionId());
                            finalizationOptions.addItem(option);
                        });
                        if (finalizationOptions.getItemCount() > 0) {
                            finalizationOptions.setSelectedIndex(0);
                        } else {
                            readiness.setText("终审状态：本学院该批次暂无招生专业");
                        }
                    }
                    loadApplications(batch.batchId());
                }));
    }

    void refreshReadiness() {
        loadSelectedOptionReadiness();
    }

    private void loadSelectedOptionReadiness() {
        setFinalizationActions(false);
        MajorTransferOptionView option =
                (MajorTransferOptionView) finalizationOptions.getSelectedItem();
        long request = ++readinessRequest;
        if (option == null) return;
        readiness.setText("终审状态：加载中…");
        optionFinalizer.loadReadiness(option.optionId(), request);
    }

    private void setFinalizationActions(boolean enabled) {
        finalizeOption.setEnabled(enabled);
        effectiveOption.setEnabled(enabled);
        rollbackOption.setEnabled(enabled);
    }

    private void loadApplications(String batchId) {
        long request = batchRequest;
        MajorTransferApplicationView selected = applications.getSelectedValue();
        String selectedId = selected == null ? null : selected.applicationId();
        students.listTransferApplications(new MajorTransferApplicationQuery(batchId, null, null))
                .whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
                    if (request != batchRequest) return;
                    model.clear();
                    if (response != null && response.success()) {
                        MajorTransferApplicationView toSelect = null;
                        for (MajorTransferApplicationView item : response.data()) {
                            model.addElement(item);
                            if (selectedId != null && selectedId.equals(item.applicationId())) {
                                toSelect = item;
                            }
                        }
                        if (toSelect != null) applications.setSelectedValue(toSelect, true);
                    } else status.setText(message(response, "申请加载失败"));
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
        MajorTransferApplicationView scoped = applications.getSelectedValue();
        if (scoped != null && scoped.applicationId().equals(app.applicationId())) {
            app = app.withReviewPermissions(scoped.sourceApprovalAllowed(),
                    scoped.targetApprovalAllowed());
        }
        for (int i = 0; i < model.getSize(); i++) {
            if (model.get(i).applicationId().equals(app.applicationId())) {
                model.set(i, app);
                break;
            }
        }
        if (workspaceView != null) workspaceView.renderApplicationDetail(app);
        MajorTransferAttachmentDownloader.render(
                this, students, attachments, status, app.attachments());
        collegeActions.render(app, ownedOptions.contains(app.optionId()));
    }

    private static String message(edu.seu.vcampus.common.protocol.ResponseBody<?> response,
            String fallback) {
        return response == null || response.message() == null ? fallback : response.message();
    }
}
