package edu.seu.vcampus.client.course.ui;
public final class AdjustmentAuditPanel extends SimpleCourseTablePanel {
 public AdjustmentAuditPanel(CourseUiGateway gateway){super("选课调整审计","查询补选、退选、改选及失败原因。","查询审计记录",new Object[]{"时间","学生","操作","来源教学班","目标教学班","结果"},new Object[][]{{"08-27 14:32","20260001","改选","物理01班","物理02班","成功"},{"08-27 14:18","20260018","补选","—","数据结构02班","容量已满"}});}
}
