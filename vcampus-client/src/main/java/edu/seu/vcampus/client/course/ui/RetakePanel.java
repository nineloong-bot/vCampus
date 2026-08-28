package edu.seu.vcampus.client.course.ui;
public final class RetakePanel extends SimpleCourseTablePanel {
 public RetakePanel(CourseUiGateway gateway){super("重修选课","仅列出历史结果为未通过的课程。","检查重修资格",new Object[]{"课程代码","课程名称","历史结果","可选教学班","状态"},new Object[][]{{"PHYS101","大学物理","未通过","01班","可重修"}});}
}
