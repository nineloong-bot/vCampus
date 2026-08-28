package edu.seu.vcampus.client.course.ui;
public final class AdjustmentPanel extends SimpleCourseTablePanel {
 public AdjustmentPanel(CourseUiGateway gateway){super("选课调整","在调整开放期内执行补选、退选和改选。","发起补选",new Object[]{"课程","原教学班","当前状态","可执行操作"},new Object[][]{{"大学物理","01班","有效","退选 / 改选"},{"数据结构","02班","有效","退选 / 改选"}});}
}
