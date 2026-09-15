package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
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
        batches.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof MajorTransferBatchView batch) {
                    setText(batch.batchName() + " [" + batch.status() + "]");
                }
                return this;
            }
        });
        batches.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                formCard.loadBatch(batches.getSelectedValue());
            }
        });

        JScrollPane scrollPane = new JScrollPane(batches);
        scrollPane.setBorder(UiBorders.LINE);
        left.add(scrollPane, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        toolbar.setOpaque(false);
        JButton create = new JButton("新建批次");
        create.addActionListener(event -> {
            batches.clearSelection();
            formCard.clearForNew();
        });
        JButton refresh = new JButton("刷新");
        refresh.addActionListener(event -> refresh());
        toolbar.add(create);
        toolbar.add(refresh);
        left.add(toolbar, BorderLayout.SOUTH);

        formCard.saveButton().addActionListener(event -> saveBatch());

        add(left, BorderLayout.WEST);
        add(formCard, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    @Override public void addNotify() {
        super.addNotify();
        refresh();
    }

    private void refresh() {
        status.setText("正在加载批次列表…");
        students.listTransferBatches().whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success()) {
                        status.setText(message(response, "批次加载失败"));
                        return;
                    }
                    model.clear();
                    response.data().forEach(model::addElement);
                    status.setText("批次列表已更新，共 " + model.size() + " 个批次");
                }));
    }

    private void saveBatch() {
        SaveMajorTransferBatchCommand command;
        try {
            command = formCard.buildCommand();
        } catch (IllegalArgumentException ex) {
            formCard.showFeedback(ex.getMessage(), true);
            return;
        }
        formCard.showFeedback("正在保存…", false);
        formCard.saveButton().setEnabled(false);
        students.saveTransferBatch(command).whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    formCard.saveButton().setEnabled(true);
                    if (response != null && response.success()) {
                        formCard.showFeedback("批次保存成功", false);
                        refresh();
                    } else {
                        formCard.showFeedback(message(response, "保存失败"), true);
                    }
                }));
    }

    private static String message(ResponseBody<?> response, String fallback) {
        return response == null || response.message() == null ? fallback : response.message();
    }
}
