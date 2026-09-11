package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;

/** Unified student course-selection page driven by the administrator-opened phase. */
public final class StudentCourseSelectionPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final JTextField keyword = new JTextField(18);
    private final JComboBox<String> weekday = new JComboBox<>(new String[]{"全部星期", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"});
    private final JComboBox<String> conflict = new JComboBox<>(new String[]{"是否冲突：全部", "仅无冲突", "仅有冲突"});
    private final JComboBox<String> nature = new JComboBox<>(new String[]{"课程性质：全部", "必修", "限选", "任选"});
    private final JComboBox<String> category = new JComboBox<>(new String[]{"课程类别：全部", "通识教育课", "大类学科基础课", "专业主干课", "专业方向课", "实践环节"});
    private final JLabel count = label("共 0 门课程", UiTypography.BODY, UiColors.TEXT_SECONDARY);
    private final JPanel courses = new JPanel();
    private final CoursePager pager;
    private final DropConfirmation confirmation;
    private final Runnable onMutation;
    private StudentCourseRowPanel expandedRow;

    public StudentCourseSelectionPanel(CourseUiGateway gateway) {
        this(gateway, (owner, courseLabel) -> JOptionPane.showConfirmDialog(owner,
                "确认取消选择“" + courseLabel + "”吗？", "确认取消选课",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION, () -> { });
    }

    StudentCourseSelectionPanel(CourseUiGateway gateway, DropConfirmation confirmation, Runnable onMutation) {
        super("选课", "按课程查看可选教学班；选课、退课和重修会根据当前阶段与学生状态自动开放。");
        this.gateway = gateway;
        this.confirmation = confirmation;
        this.onMutation = onMutation;
        setPageTitleFont(UiTypography.DISPLAY);
        this.pager = new CoursePager(20, this::loadPage);
        keyword.getAccessibleContext().setAccessibleName("课程关键词");
        weekday.getAccessibleContext().setAccessibleName("上课日期");
        conflict.getAccessibleContext().setAccessibleName("是否冲突");
        nature.getAccessibleContext().setAccessibleName("课程性质");
        category.getAccessibleContext().setAccessibleName("课程类别");
        courses.setLayout(new BoxLayout(courses, BoxLayout.Y_AXIS));
        courses.setOpaque(false);
        body.add(filters(), BorderLayout.NORTH);
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));listing.setOpaque(false);
        JPanel listTop = new JPanel(new BorderLayout()); listTop.setOpaque(false);
        listTop.add(count, BorderLayout.NORTH); listTop.add(tableHeader(), BorderLayout.SOUTH);
        listing.add(listTop, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(courses);scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));scroll.getVerticalScrollBar().setUnitIncrement(18);
        listing.add(scroll, BorderLayout.CENTER);listing.add(pager, BorderLayout.SOUTH);body.add(listing, BorderLayout.CENTER);
        refresh();
    }

    private JPanel filters() {
        JPanel panel = new JPanel(new BorderLayout(UiSpacing.MD, 0));
        panel.setBackground(UiColors.BACKGROUND_SUBTLE);
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.MD, UiSpacing.LG));
        fields.setOpaque(false);
        fields.add(label("课程关键词", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        keyword.setPreferredSize(new Dimension(220, UiDimensions.CONTROL_HEIGHT));
        keyword.addActionListener(e -> refresh());
        fields.add(keyword);
        fields.add(label("上课日期", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        weekday.setPreferredSize(new Dimension(128, UiDimensions.CONTROL_HEIGHT));
        fields.add(weekday);
        fields.add(conflict);
        fields.add(nature);
        fields.add(category);
        panel.add(fields, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SM, UiSpacing.LG));
        actions.setOpaque(false);
        JButton query = primary("搜索");
        query.addActionListener(e -> refresh());
        actions.add(query);
        JButton reset = secondary("重置");
        reset.addActionListener(e -> {
            keyword.setText("");
            weekday.setSelectedIndex(0);
            conflict.setSelectedIndex(0);
            nature.setSelectedIndex(0);
            category.setSelectedIndex(0);
            refresh();
        });
        actions.add(reset);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JPanel tableHeader() {
        JPanel header = new JPanel(new GridLayout(1, 7));
        header.setBackground(new Color(235, 243, 253));
        header.setBorder(BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.MD, UiSpacing.MD, UiSpacing.MD));
        for (String text : new String[]{"课程号", "课程名称", "教学班个数", "课程性质", "开课单位", "学分", ""}) {
            JLabel label = label(text, UiTypography.BODY_BOLD, UiColors.TEXT_PRIMARY); header.add(label);
        }
        header.getAccessibleContext().setAccessibleName("可选课程表头");
        return header;
    }

    public void refresh() {
        loadPage(0);
    }

    private void loadPage(int pageNumber) {
        String keywordSnapshot=keyword.getText();String weekdaySnapshot=selectedDay();
        Boolean conflictSnapshot=switch(conflict.getSelectedIndex()){case 1->Boolean.FALSE;case 2->Boolean.TRUE;default->null;};
        String natureSnapshot=switch(nature.getSelectedIndex()){case 1->"REQUIRED";case 2->"RESTRICTED";case 3->"ELECTIVE";default->null;};
        String categorySnapshot=category.getSelectedIndex()==0?null:(String)category.getSelectedItem();
        long request=beginAsyncRequest();showState(ViewState.LOADING,"正在加载选课信息，请稍候");
        gateway.studentSelectionContext().thenCompose(value->gateway.searchStudentCourses(new CourseSelectionQuery(value.termId(),keywordSnapshot,weekdaySnapshot,conflictSnapshot,natureSnapshot,categorySnapshot,pageNumber,20)).thenApply(page->new SelectionData(value,page)))
                .whenComplete((data,error)->SwingUtilities.invokeLater(()->{
                    if(!acceptsAsyncResult(request))return;
                    if(error!=null){showState(ViewState.DISCONNECTED,"无法加载选课信息，请检查连接后重试");return;}
                    StudentSelectionContextView context=data.context();var page=data.page(); expandedRow=null;
                    setPageTitle(context.displayTitle()==null?"选课":context.displayTitle());courses.removeAll();
                    for(CourseSelectionView course:page.items())courses.add(card(course));
                    count.setText("共 "+page.total()+" 门课程");courses.revalidate();courses.repaint();
                    pager.showPage(page.page(),page.total());
                    if(context.displayTitle()==null)showState(ViewState.EMPTY,"管理员尚未开放选课阶段，可先查看课程信息");
                    else if("PREVIEW".equals(context.phaseStatus()))showState(ViewState.CONFLICT,"预选课阶段，仅可查看课程和教学班");
                    else if(!context.studentEligible())showState(ViewState.ERROR,context.ineligibleReason());
                    else showState(page.items().isEmpty()?ViewState.EMPTY:ViewState.NORMAL,page.items().isEmpty()?"未找到符合条件的课程":"");
                }));
    }

    @Override protected void refreshAfterNavigation(){refresh();}

    private JPanel card(CourseSelectionView course) {
        JPanel optionsPanel=new JPanel(new FlowLayout(FlowLayout.LEFT,UiSpacing.MD,UiSpacing.MD));
        optionsPanel.setBackground(new Color(244,245,247));
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(UiSpacing.MD,UiSpacing.XL,UiSpacing.MD,UiSpacing.XL));
        for(TeachingClassOptionView option:course.teachingClasses()){
            OfferingSummary offering=option.offering();
            boolean selected=offering.offeringId().equals(course.activeOfferingId());
            JButton action=primary(selected?"退选":"选择");
            action.setEnabled(selected?"CANCEL_SELECTION".equals(course.courseAction()):isSelectable(option));
            action.setToolTipText(option.actionReason()==null?course.courseReason():option.actionReason());
            action.getAccessibleContext().setAccessibleName(
                    (selected?"退选教学班 ":"选择教学班 ")+offering.className());
            action.addActionListener(e->{
                if(selected){
                    if(confirmation.confirm(SwingUtilities.getWindowAncestor(this),course.courseCode()+" "+course.courseName()))
                        submit(action,gateway.drop(new DropCommand(course.activeEnrollmentId(),course.activeEnrollmentVersion())));
                    return;
                }
                String id=offering.offeringId();
                CompletableFuture<?> operation=switch(option.actionType()){
                    case "RETAKE"->gateway.enrollRetake(new RetakeCommand(id));
                    case "LATE_ADD"->gateway.lateAdd(new LateAddCommand(id));
                    default->gateway.enroll(new EnrollCommand(id));
                };
                submit(action,operation);
            });
            optionsPanel.add(new TeachingClassCardPanel(option,action));
        }
        StudentCourseRowPanel row=new StudentCourseRowPanel(course,optionsPanel,this::expandOnly);
        return row;
    }

    private void expandOnly(StudentCourseRowPanel row){if(expandedRow!=null&&expandedRow!=row)expandedRow.collapse();expandedRow=row;row.expand();}

    private void submit(JButton button,CompletableFuture<?> operation){long request=beginAsyncRequest();button.setEnabled(false);showState(ViewState.SUBMITTING,"正在提交，请勿重复操作");operation.whenComplete((ignored,error)->SwingUtilities.invokeLater(()->{if(!acceptsAsyncResult(request))return;if(error!=null){button.setEnabled(true);showState(ViewState.ERROR,"操作未完成，请刷新后重试");}else{onMutation.run();refresh();}}));}
    private static boolean isSelectable(TeachingClassOptionView option){return java.util.Set.of("ENROLL","RETAKE","LATE_ADD").contains(option.actionType());}
    private String selectedDay(){return switch(weekday.getSelectedIndex()){case 1->"MONDAY";case 2->"TUESDAY";case 3->"WEDNESDAY";case 4->"THURSDAY";case 5->"FRIDAY";case 6->"SATURDAY";case 7->"SUNDAY";default->null;};}
    static String scheduleText(OfferingSummary o){if(o.schedules().isEmpty())return "待安排";return o.schedules().stream().map(s->dayName(s.dayOfWeek())+" 第"+s.startPeriod()+"–"+s.endPeriod()+"节 "+s.classroom()).collect(java.util.stream.Collectors.joining("；"));}
    private static String dayName(String day){return switch(day){case "MONDAY"->"星期一";case "TUESDAY"->"星期二";case "WEDNESDAY"->"星期三";case "THURSDAY"->"星期四";case "FRIDAY"->"星期五";case "SATURDAY"->"星期六";case "SUNDAY"->"星期日";default->day;};}
    private record SelectionData(StudentSelectionContextView context,edu.seu.vcampus.common.paging.PageResult<CourseSelectionView> page){}
}
