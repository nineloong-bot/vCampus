package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Administrator page for explicitly naming, opening, and closing course-selection phases. */
public final class SelectionPhaseManagementPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final JComboBox<TermChoice> term = new JComboBox<>();
    private final JComboBox<String> type = new JComboBox<>(new String[]{"正常选课", "退改补选课"});
    private final JTextField title = new JTextField(24);
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"学期", "阶段类型", "学生端标题", "状态", "更新时间", "版本"},0){public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=table(new Object[0][0],new Object[0]);
    private final List<SelectionPhaseView> phases=new ArrayList<>();
    private final JButton create=primary("新建阶段"),save=secondary("保存标题"),open=primary("开放选课"),close=secondary("关闭阶段"),refresh=secondary("刷新");
    private boolean pending;

    public SelectionPhaseManagementPanel(CourseUiGateway gateway){
        super("选课阶段","独立管理选课开放状态和学生端标题；任意时刻只允许一个阶段开放。");this.gateway=gateway;
        term.getAccessibleContext().setAccessibleName("学期");type.getAccessibleContext().setAccessibleName("选课阶段类型");title.getAccessibleContext().setAccessibleName("学生端标题");
        body.add(editor(),BorderLayout.NORTH);table.setModel(model);table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())showSelected();});
        JScrollPane scroll=new JScrollPane(table);scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));body.add(scroll,BorderLayout.CENTER);updateActions();load();
    }

    private JPanel editor(){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,UiSpacing.SM,UiSpacing.SM));p.setBackground(UiColors.BACKGROUND_SUBTLE);
        p.add(label("学期",UiTypography.BODY,UiColors.TEXT_PRIMARY));p.add(term);p.add(label("阶段",UiTypography.BODY,UiColors.TEXT_PRIMARY));p.add(type);p.add(label("学生端标题",UiTypography.BODY,UiColors.TEXT_PRIMARY));p.add(title);
        create.addActionListener(e->create());p.add(create);save.addActionListener(e->save());p.add(save);open.addActionListener(e->change("OPEN"));p.add(open);close.addActionListener(e->{SelectionPhaseView value=selected();if(value!=null&&JOptionPane.showConfirmDialog(this,"关闭后不能重新开放，确认关闭吗？","确认关闭阶段",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION)change("CLOSED");});p.add(close);refresh.addActionListener(e->load());p.add(refresh);return p;}

    private void load(){long request=beginAsyncRequest();pending=true;updateActions();showState(ViewState.LOADING,"正在加载选课阶段");gateway.listTerms().thenCombine(gateway.listSelectionPhases(),PhaseData::new).whenComplete((data,error)->SwingUtilities.invokeLater(()->{if(!acceptsAsyncResult(request))return;pending=false;if(error!=null){updateActions();showState(ViewState.DISCONNECTED,"无法加载选课阶段");return;}term.removeAllItems();for(TermView value:data.terms())term.addItem(new TermChoice(value.termId(),value.termName(),value.termStatus()));phases.clear();phases.addAll(data.phases());model.setRowCount(0);for(SelectionPhaseView phase:data.phases())model.addRow(new Object[]{termName(phase.termId()),typeName(phase.phaseType()),phase.displayTitle(),statusName(phase.phaseStatus()),java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault()).format(phase.updatedAt()),"v"+phase.rowVersion()});updateActions();showState(data.phases().isEmpty()?ViewState.EMPTY:ViewState.NORMAL,data.phases().isEmpty()?"尚未配置选课阶段":"");}));}
    @Override protected void refreshAfterNavigation(){load();}
    private SelectionPhaseView selected(){int row=table.getSelectedRow();return row<0?null:phases.get(table.convertRowIndexToModel(row));}
    private void showSelected(){SelectionPhaseView value=selected();if(value!=null)title.setText(value.displayTitle());updateActions();}
    private void create(){TermChoice choice=(TermChoice)term.getSelectedItem();if(choice==null||title.getText().isBlank()){showState(ViewState.ERROR,"请选择学期并填写学生端标题");return;}submit(gateway.createSelectionPhase(new CreateSelectionPhaseCommand(choice.id(),type.getSelectedIndex()==0?"ENROLLMENT":"ADJUSTMENT",title.getText().trim())));}
    private void save(){SelectionPhaseView value=selected();if(value==null||title.getText().isBlank()){showState(ViewState.ERROR,"请选择待修改的草稿阶段");return;}submit(gateway.updateSelectionPhase(new UpdateSelectionPhaseCommand(value.phaseId(),title.getText().trim(),value.rowVersion())));}
    private void change(String status){SelectionPhaseView value=selected();if(value==null){showState(ViewState.ERROR,"请先选择一个阶段");return;}submit(gateway.changeSelectionPhaseStatus(new ChangeSelectionPhaseStatusCommand(value.phaseId(),status,value.rowVersion())));}
    private void submit(java.util.concurrent.CompletableFuture<?> operation){long request=beginAsyncRequest();pending=true;updateActions();showState(ViewState.SUBMITTING,"正在保存选课阶段");operation.whenComplete((ignored,error)->SwingUtilities.invokeLater(()->{if(!acceptsAsyncResult(request))return;pending=false;if(error!=null){updateActions();Throwable cause=error;while(cause instanceof java.util.concurrent.CompletionException&&cause.getCause()!=null)cause=cause.getCause();showState(ViewState.ERROR,cause.getMessage()==null?"操作未完成，请刷新后重试":cause.getMessage());}else load();}));}
    private void updateActions(){SelectionPhaseView value=selected();create.setEnabled(!pending);refresh.setEnabled(!pending);save.setEnabled(!pending&&value!=null&&"DRAFT".equals(value.phaseStatus()));open.setEnabled(!pending&&value!=null&&"DRAFT".equals(value.phaseStatus()));close.setEnabled(!pending&&value!=null&&"OPEN".equals(value.phaseStatus()));}
    private String termName(String id){for(int i=0;i<term.getItemCount();i++)if(term.getItemAt(i).id().equals(id))return term.getItemAt(i).name();return id;}
    private static String typeName(String value){return "ENROLLMENT".equals(value)?"正常选课":"退改补选课";}
    private static String statusName(String value){return switch(value){case "DRAFT"->"草稿";case "OPEN"->"已开放";case "CLOSED"->"已关闭";default->value;};}
    private record TermChoice(String id,String name,String status){public String toString(){return name+("ACTIVE".equals(status)?"  (进行中)":"");}}
    private record PhaseData(List<TermView> terms,List<SelectionPhaseView> phases){}
}
