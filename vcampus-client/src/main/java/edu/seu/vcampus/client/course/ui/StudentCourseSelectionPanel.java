package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.*;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Unified student course-selection page driven by the administrator-opened phase. */
public final class StudentCourseSelectionPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final JTextField keyword = new JTextField(18);
    private final JComboBox<String> weekday = new JComboBox<>(new String[]{"全部星期", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"});
    private final JLabel count = label("共 0 门课程", UiTypography.BODY, UiColors.TEXT_SECONDARY);
    private final JPanel courses = new JPanel();
    private final CoursePager pager;
    private final DropConfirmation confirmation;
    private final Runnable onMutation;

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
        courses.setLayout(new BoxLayout(courses, BoxLayout.Y_AXIS));
        courses.setOpaque(false);
        body.add(filters(), BorderLayout.NORTH);
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));listing.setOpaque(false);
        listing.add(count, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(courses);scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));scroll.getVerticalScrollBar().setUnitIncrement(18);
        listing.add(scroll, BorderLayout.CENTER);listing.add(pager, BorderLayout.SOUTH);body.add(listing, BorderLayout.CENTER);
        refresh();
    }

    private JPanel filters() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.MD, UiSpacing.LG));panel.setBackground(UiColors.BACKGROUND_SUBTLE);
        panel.add(label("课程关键词", UiTypography.BODY, UiColors.TEXT_PRIMARY));keyword.setPreferredSize(new Dimension(220, UiDimensions.CONTROL_HEIGHT));panel.add(keyword);
        panel.add(label("上课日期", UiTypography.BODY, UiColors.TEXT_PRIMARY));weekday.setPreferredSize(new Dimension(128, UiDimensions.CONTROL_HEIGHT));panel.add(weekday);
        JButton query=primary("查询课程");query.addActionListener(e->refresh());panel.add(query);
        JButton reset=secondary("重置条件");reset.addActionListener(e->{keyword.setText("");weekday.setSelectedIndex(0);refresh();});panel.add(reset);return panel;
    }

    public void refresh() {
        loadPage(0);
    }

    private void loadPage(int pageNumber) {
        String keywordSnapshot=keyword.getText();String weekdaySnapshot=selectedDay();
        long request=beginAsyncRequest();showState(ViewState.LOADING,"正在加载选课信息，请稍候");
        gateway.studentSelectionContext().thenCompose(value->gateway.searchStudentCourses(new CourseSelectionQuery(value.termId(),keywordSnapshot,weekdaySnapshot,pageNumber,20)).thenApply(page->new SelectionData(value,page)))
                .whenComplete((data,error)->SwingUtilities.invokeLater(()->{
                    if(!acceptsAsyncResult(request))return;
                    if(error!=null){showState(ViewState.DISCONNECTED,"无法加载选课信息，请检查连接后重试");return;}
                    StudentSelectionContextView context=data.context();var page=data.page();
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
        JPanel card=new JPanel(new BorderLayout());card.setBackground(UiColors.BACKGROUND_PAGE);card.setBorder(BorderFactory.createMatteBorder(0,0,1,0,UiColors.BORDER_DEFAULT));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        JPanel header=new JPanel(new BorderLayout(UiSpacing.LG,0));header.setOpaque(false);header.setBorder(BorderFactory.createEmptyBorder(UiSpacing.MD,UiSpacing.LG,UiSpacing.MD,UiSpacing.LG));
        JButton expand=new JButton("▶  "+course.courseCode()+"  "+course.courseName()+"   ("+course.teachingClasses().size()+"个教学班)");expand.setFont(UiTypography.BODY_BOLD);expand.setHorizontalAlignment(SwingConstants.LEFT);expand.setBorderPainted(false);expand.setContentAreaFilled(false);
        header.add(expand,BorderLayout.CENTER);
        JPanel optionsPanel=new JPanel();optionsPanel.setLayout(new BoxLayout(optionsPanel,BoxLayout.Y_AXIS));optionsPanel.setBackground(UiColors.BACKGROUND_SUBTLE);optionsPanel.setBorder(BorderFactory.createEmptyBorder(UiSpacing.SM,UiSpacing.XL,UiSpacing.MD,UiSpacing.XL));optionsPanel.setVisible(false);
        ButtonGroup group=new ButtonGroup();Map<AbstractButton,TeachingClassOptionView> choices=new LinkedHashMap<>();
        for(TeachingClassOptionView option:course.teachingClasses()){
            OfferingSummary offering=option.offering();String reason=option.actionReason()==null?"":("  —  "+option.actionReason());
            JRadioButton radio=new JRadioButton(offering.className()+"  教师 "+offering.teacherUserId()+"  "+schedule(offering)+"  "+(offering.capacity()-offering.enrolledCount())+" / "+offering.capacity()+reason);
            radio.setOpaque(false);radio.setFont(UiTypography.BODY);radio.setEnabled(isSelectable(option));radio.setSelected(offering.offeringId().equals(course.activeOfferingId()));group.add(radio);optionsPanel.add(radio);choices.put(radio,option);
        }
        expand.addActionListener(e->{boolean show=!optionsPanel.isVisible();optionsPanel.setVisible(show);card.setMaximumSize(new Dimension(Integer.MAX_VALUE,show?80+course.teachingClasses().size()*34:64));expand.setText((show?"▼  ":"▶  ")+course.courseCode()+"  "+course.courseName()+"   ("+course.teachingClasses().size()+"个教学班)");card.revalidate();});
        JButton action=primary("CANCEL_SELECTION".equals(course.courseAction())?"取消选课":actionLabel(choices));
        action.setEnabled("CANCEL_SELECTION".equals(course.courseAction()));action.setToolTipText(course.courseReason());
        choices.forEach((button,option)->button.addItemListener(e->{if(button.isSelected()){action.setText(optionLabel(option));action.setEnabled(true);}}));
        action.addActionListener(e->{
            if("CANCEL_SELECTION".equals(course.courseAction())){if(confirmation.confirm(SwingUtilities.getWindowAncestor(this),course.courseCode()+" "+course.courseName()))submit(action,gateway.drop(new DropCommand(course.activeEnrollmentId(),course.activeEnrollmentVersion())));return;}
            TeachingClassOptionView selected=choices.entrySet().stream().filter(x->x.getKey().isSelected()).map(Map.Entry::getValue).findFirst().orElse(null);
            if(selected==null){optionsPanel.setVisible(true);showState(ViewState.ERROR,"请先展开课程并选择一个可用教学班");return;}
            String id=selected.offering().offeringId();CompletableFuture<?> operation=switch(selected.actionType()){case "RETAKE"->gateway.enrollRetake(new RetakeCommand(id));case "LATE_ADD"->gateway.lateAdd(new LateAddCommand(id));default->gateway.enroll(new EnrollCommand(id));};submit(action,operation);
        });
        header.add(action,BorderLayout.EAST);card.add(header,BorderLayout.NORTH);card.add(optionsPanel,BorderLayout.CENTER);return card;
    }

    private void submit(JButton button,CompletableFuture<?> operation){long request=beginAsyncRequest();button.setEnabled(false);showState(ViewState.SUBMITTING,"正在提交，请勿重复操作");operation.whenComplete((ignored,error)->SwingUtilities.invokeLater(()->{if(!acceptsAsyncResult(request))return;if(error!=null){button.setEnabled(true);showState(ViewState.ERROR,"操作未完成，请刷新后重试");}else{onMutation.run();refresh();}}));}
    private static boolean isSelectable(TeachingClassOptionView option){return java.util.Set.of("ENROLL","RETAKE","LATE_ADD").contains(option.actionType());}
    private String selectedDay(){return switch(weekday.getSelectedIndex()){case 1->"MONDAY";case 2->"TUESDAY";case 3->"WEDNESDAY";case 4->"THURSDAY";case 5->"FRIDAY";case 6->"SATURDAY";case 7->"SUNDAY";default->null;};}
    private static String actionLabel(Map<AbstractButton,TeachingClassOptionView> choices){return choices.values().stream().filter(StudentCourseSelectionPanel::isSelectable).findFirst().map(StudentCourseSelectionPanel::optionLabel).orElse("选择课程");}
    private static String optionLabel(TeachingClassOptionView option){return switch(option.actionType()){case "RETAKE"->"重修选课";case "LATE_ADD"->"补选课程";default->"选择课程";};}
    private static String schedule(OfferingSummary o){if(o.schedules().isEmpty())return "待安排";return o.schedules().stream().map(s->dayName(s.dayOfWeek())+" 第"+s.startPeriod()+"–"+s.endPeriod()+"节 "+s.classroom()).collect(java.util.stream.Collectors.joining("；"));}
    private static String dayName(String day){return switch(day){case "MONDAY"->"星期一";case "TUESDAY"->"星期二";case "WEDNESDAY"->"星期三";case "THURSDAY"->"星期四";case "FRIDAY"->"星期五";case "SATURDAY"->"星期六";case "SUNDAY"->"星期日";default->day;};}
    private record SelectionData(StudentSelectionContextView context,edu.seu.vcampus.common.paging.PageResult<CourseSelectionView> page){}
}
