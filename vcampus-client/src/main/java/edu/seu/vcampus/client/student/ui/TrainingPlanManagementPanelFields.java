package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.CourseType;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;
import edu.seu.vcampus.common.student.TrainingPlanCourseView;
import edu.seu.vcampus.common.student.TrainingPlanDetailView;

import javax.swing.*;
import java.awt.*;

/** Shared Swing state and the student service used by every training plan panel segment. */
abstract class TrainingPlanManagementPanelFields extends JPanel {
    /** Service used for all training plan data exchanges. */
    protected final StudentClientService students;
    protected final TrainingPlanCourseTableModel courseModel = new TrainingPlanCourseTableModel();
    protected final JLabel statusLabel = new JLabel("就绪");
    protected final JTable courseTable = new JTable(courseModel);
    protected final JComboBox<DepartmentView> deptBox = new JComboBox<>();
    protected final JComboBox<MajorView> majorBox = new JComboBox<>();
    protected final JComboBox<String> yearBox = new JComboBox<>();
    protected final JLabel planInfoLabel = new JLabel("请选择院系、专业和年级");
    protected TrainingPlanDetailView currentPlan;

    protected final CardLayout workspaceCardLayout = new CardLayout();
    protected final JPanel workspaceCardPanel = new JPanel(workspaceCardLayout);
    protected javax.swing.border.TitledBorder courseBorder;
    protected javax.swing.border.TitledBorder planBorder;
    protected JTextField courseCodeField, courseNameField, courseCreditsField;
    protected JComboBox<CourseType> courseTypeBox;
    protected JComboBox<String> courseSemesterBox;
    protected JLabel courseMsgLabel;
    protected TrainingPlanCourseView editingCourse;

    protected JTextField planNameField, minCountField, minCreditsField;
    protected JLabel planMsgLabel;
    protected boolean isEditingPlan;

    /** Stores the service shared by the panel logic segments. */
    protected TrainingPlanManagementPanelFields(StudentClientService students) {
        this.students = students;
    }
}
