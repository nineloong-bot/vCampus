package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/** Administrator page for explicitly maintaining course-selection phases. */
public final class SelectionPhaseManagementPanel extends AbstractCoursePanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final CourseUiGateway gateway;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"学期", "阶段类型", "学生端标题", "状态", "更新时间", "版本"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = table(new Object[0][0], new Object[0]);
    private final List<SelectionPhaseView> phases = new ArrayList<>();
    private final List<TermView> terms = new ArrayList<>();
    private final EmbeddedEditorHost editorHost;

    /** Creates the selection-phase management page. */
    public SelectionPhaseManagementPanel(CourseUiGateway gateway) {
        super("选课阶段");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));
        listing.setOpaque(false);
        listing.add(toolbar(), BorderLayout.NORTH);
        table.setModel(model);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        listing.add(scroll, BorderLayout.CENTER);
        editorHost = new EmbeddedEditorHost(listing);
        body.add(editorHost, BorderLayout.CENTER);
        load();
    }

    private JPanel toolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SM, 0));
        toolbar.setOpaque(false);
        JButton refresh = secondary("刷新"); refresh.addActionListener(event -> load()); toolbar.add(refresh);
        JButton edit = secondary("编辑所选"); edit.addActionListener(event -> editSelected()); toolbar.add(edit);
        JButton create = primary("新建阶段"); create.addActionListener(event -> openEditor(null)); toolbar.add(create);
        return toolbar;
    }

    private void load() {
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在加载选课阶段");
        gateway.listTerms().thenCombine(gateway.listSelectionPhases(), PhaseData::new)
                .whenComplete((data, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!acceptsAsyncResult(request)) return;
                    if (failure != null) { showState(ViewState.DISCONNECTED, "无法加载选课阶段"); return; }
                    terms.clear(); terms.addAll(data.terms()); phases.clear(); phases.addAll(data.phases());
                    model.setRowCount(0);
                    for (SelectionPhaseView phase : phases) model.addRow(new Object[]{
                            termName(phase.termId()), typeName(phase.phaseType()), phase.displayTitle(),
                            statusName(phase.phaseStatus()), TIME.format(phase.updatedAt()), "v" + phase.rowVersion()});
                    showState(phases.isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                            phases.isEmpty() ? "尚未配置选课阶段" : "");
                }));
    }

    @Override protected void refreshAfterNavigation() { load(); }

    private void editSelected() {
        int selected = table.getSelectedRow();
        if (selected < 0) { showState(ViewState.ERROR, "请先选择一个阶段"); return; }
        openEditor(phases.get(table.convertRowIndexToModel(selected)));
    }

    private void openEditor(SelectionPhaseView phase) {
        editorHost.showEditor(new SelectionPhaseEditorPanel(gateway, List.copyOf(terms), phase, () -> {
            load(); editorHost.completeAndClose();
        }, editorHost::requestClose));
    }

    private String termName(String id) { return terms.stream().filter(term -> term.termId().equals(id))
            .map(TermView::termName).findFirst().orElse(id); }
    private static String typeName(String value) { return "ENROLLMENT".equals(value) ? "正常选课" : "退改补选课"; }
    private static String statusName(String value) { return switch (value) {
        case "DRAFT" -> "草稿"; case "PREVIEW" -> "预选课"; case "OPEN" -> "正式开放";
        case "CLOSED" -> "已关闭"; default -> value;
    }; }
    private record PhaseData(List<TermView> terms, List<SelectionPhaseView> phases) { }
}
