package edu.seu.vcampus.client.course.ui;
public final class CourseCatalogPanel extends SimpleCourseTablePanel {
 public CourseCatalogPanel(CourseUiGateway gateway){super("课程目录管理","维护课程代码、名称、学分与启用状态。","新建课程",new Object[]{"课程代码","课程名称","学分","状态","版本"},new Object[][]{{"MATH101","高等数学","5","启用","v2"},{"CS201","数据结构","4","启用","v1"}});}
}
