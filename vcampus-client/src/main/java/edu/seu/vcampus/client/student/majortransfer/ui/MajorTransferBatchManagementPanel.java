package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferBatchCommand;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/** Manages school-wide transfer batches for central student admin with in-workspace editing. */
public final class MajorTransferBatchManagementPanel extends JPanel {
    private final StudentClientService students;
    private final DefaultListModel<MajorTransferBatchView> model = new DefaultListModel<>();
    private final JList<MajorTransferBatchView> batches = new JList<>(model);
    private final MajorTransferBatchFormCardPanel formCard = new MajorTransferBatchFormCardPanel();
    private final JLabel status = new JLabel(" ");
    private final JButton createButton = new JButton("新建批次");
    private final JButton refreshButton = new JButton("刷新");
    private EmbeddedEditorHost editorHost;
    private MajorTransferBatchEditorPanel currentEditor;
    private int refreshSequence;

    /** Creates the in-workspace batch management workspace. */
    public MajorTransferBatchManagementPanel(StudentClientService students) {
        super(new BorderLayout(UiSpacing.SPACE_3, 0));
        this.students = Objects.requireNonNull(students, "students");
        setName("major-transfer.batch-management");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        build();
    }

    private void build() {
        JPanel left = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(320, 0));

        JLabel title = new JLabel("转专业批次");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        left.add(title, BorderLayout.NORTH);

        batches.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        batches.setName("major-transfer.batch-list");
        batches.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof MajorTransferBatchView batch) {
                    setText(batch.batchName() + " · "
                            + MajorTransferBatchStatusRenderer.text(batch.status()));
                }
                return this;
            }
        });
        JScrollPane scrollPane = new JScrollPane(batches);
        scrollPane.setBorder(UiBorders.LINE);
        left.add(scrollPane, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        toolbar.setOpaque(false);
        createButton.setName("major-transfer.batch-create");
        createButton.addActionListener(event -> {
            if (!editorHost.requestClose()) return;
            batches.clearSelection();
            formCard.clearForNew();
            showForm();
        });
        JButton editButton = new JButton("编辑所选");
        editButton.setName("major-transfer.batch-edit");
        editButton.addActionListener(event -> {
            MajorTransferBatchView selected = batches.getSelectedValue();
            if (selected == null) {
                status.setText("请先选择一个批次");
                return;
            }
            if (!editorHost.requestClose()) return;
            formCard.loadBatch(selected);
            showForm();
        });
        refreshButton.setName("major-transfer.batch-refresh");
        refreshButton.addActionListener(event -> refresh());
        toolbar.add(createButton);
        toolbar.add(editButton);
        toolbar.add(refreshButton);
        left.add(toolbar, BorderLayout.SOUTH);

        formCard.saveButton().addActionListener(event -> saveBatch());

        status.setName("major-transfer.batch-status");
        status.setFont(UiTypography.CAPTION);
        status.setForeground(UiColors.TEXT_SECONDARY);
        JPanel list = new JPanel(new BorderLayout());
        list.add(left, BorderLayout.CENTER);
        list.add(status, BorderLayout.SOUTH);
        editorHost = new EmbeddedEditorHost(list);
        add(editorHost, BorderLayout.CENTER);
    }

    @Override public void addNotify() {
        super.addNotify();
        refresh();
    }

    private void refresh() {
        MajorTransferBatchView selected = batches.getSelectedValue();
        refresh(selected == null ? null : selected.batchId());
    }

    private void refresh(String preferredBatchId) {
        int requestSequence = ++refreshSequence;
        status.setText("正在加载批次列表…");
        setBusy(true);
        students.listTransferBatches().whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (requestSequence != refreshSequence) return;
                    setBusy(false);
                    if (response == null || !response.success()) {
                        status.setText(message(response, failure, "批次加载失败，请稍后重试"));
                        return;
                    }
                    if (response.data() == null) {
                        status.setText("批次加载失败：服务端未返回数据");
                        return;
                    }
                    model.clear();
                    response.data().forEach(model::addElement);
                    selectBatch(preferredBatchId);
                    status.setText("批次列表已更新，共 " + model.size() + " 个批次");
                }));
    }

    private void selectBatch(String batchId) {
        if (batchId == null) return;
        for (int index = 0; index < model.size(); index++) {
            if (batchId.equals(model.get(index).batchId())) {
                batches.setSelectedIndex(index);
                batches.ensureIndexIsVisible(index);
                return;
            }
        }
    }

    private void saveBatch() {
        MajorTransferBatchEditorPanel expected = currentEditor;
        SaveMajorTransferBatchCommand command;
        try {
            command = formCard.buildCommand();
        } catch (IllegalArgumentException ex) {
            formCard.showFeedback(ex.getMessage(), true);
            return;
        }
        formCard.showFeedback("正在保存…", false);
        setBusy(true);
        students.saveTransferBatch(command).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response != null && response.success()) {
                        MajorTransferBatchView saved = response.data();
                        if (saved != null) formCard.loadBatch(saved);
                        formCard.showFeedback("批次保存成功", false);
                        editorHost.completeAndClose(expected);
                        refresh(saved == null ? command.batchId() : saved.batchId());
                    } else {
                        setBusy(false);
                        formCard.showFeedback(message(response, failure, "保存失败，请稍后重试"), true);
                    }
                }));
    }

    private void showForm() {
        MajorTransferBatchEditorPanel[] expected = new MajorTransferBatchEditorPanel[1];
        expected[0] = new MajorTransferBatchEditorPanel(formCard,
                () -> editorHost.requestClose(expected[0]));
        currentEditor = expected[0];
        editorHost.showEditor(currentEditor);
    }

    private void setBusy(boolean busy) {
        createButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
        batches.setEnabled(!busy);
        formCard.setBusy(busy);
    }

    private static String message(ResponseBody<?> response, Throwable failure, String fallback) {
        if (response != null && response.message() != null && !response.message().isBlank()) {
            return response.message();
        }
        return failure == null ? fallback : "网络请求失败，请稍后重试";
    }
}
