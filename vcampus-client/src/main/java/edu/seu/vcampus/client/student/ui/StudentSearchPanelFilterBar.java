package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import java.awt.*;

/** Filter bar assembly for the student search panel. */
abstract class StudentSearchPanelFilterBar extends StudentSearchPanelSearching {

    /** Creates the filter-bar segment of the student search panel. */
    protected StudentSearchPanelFilterBar(StudentClientService students, ClientConnection connection,
            boolean canEdit) {
        super(students, connection, canEdit);
    }

    JPanel buildFilterBar() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setOpaque(false);
        bar.setName("student.search.filters");
        keywordField = new JTextField(12);
        keywordField.setName("student.search.keyword");
        keywordField.setFont(UiTypography.BODY);
        keywordField.setBorder(UiBorders.LINE);
        keywordField.getAccessibleContext().setAccessibleName("搜索关键词");
        keywordField.addActionListener(e -> search());
        departmentCombo = new JComboBox<>();
        departmentCombo.setName("student.search.department");
        departmentCombo.setFont(UiTypography.BODY);
        departmentCombo.getAccessibleContext().setAccessibleName("院系筛选");
        departmentCombo.addItem("全部");
        departmentCombo.addActionListener(e -> {
            if (suppressComboEvents) return;
            Object item = departmentCombo.getSelectedItem();
            String id = (item instanceof DepartmentView d) ? d.departmentId() : null;
            long generation = requestGeneration.incrementAndGet();
            cascadeLoadMajors(id, generation);
            executeSearch(generation);
        });
        majorCombo = new JComboBox<>();
        majorCombo.setName("student.search.major");
        majorCombo.setFont(UiTypography.BODY);
        majorCombo.getAccessibleContext().setAccessibleName("专业筛选");
        majorCombo.addItem("全部");
        majorCombo.addActionListener(e -> {
            if (suppressComboEvents) return;
            Object item = majorCombo.getSelectedItem();
            String id = (item instanceof MajorView m) ? m.majorId() : null;
            long generation = requestGeneration.incrementAndGet();
            cascadeLoadClasses(id, generation);
            executeSearch(generation);
        });
        classCombo = new JComboBox<>();
        classCombo.setName("student.search.class");
        classCombo.setFont(UiTypography.BODY);
        classCombo.getAccessibleContext().setAccessibleName("班级筛选");
        classCombo.addItem("全部");
        statusCombo = new JComboBox<>(new String[]{"全部", "正常", "休学", "已毕业", "已退学"});
        statusCombo.setName("student.search.status");
        statusCombo.setFont(UiTypography.BODY);
        statusCombo.getAccessibleContext().setAccessibleName("状态筛选");
        statusCombo.addActionListener(e -> search());
        searchButton = new JButton("搜索");
        searchButton.setName("student.search.submit");
        searchButton.setFont(UiTypography.BODY);
        searchButton.setBackground(UiColors.ACCENT);
        searchButton.setForeground(UiColors.TEXT_ON_PRIMARY);
        searchButton.setBorder(BorderFactory.createCompoundBorder(UiBorders.LINE,
                BorderFactory.createEmptyBorder(UiSpacing.SPACE_1, UiSpacing.SPACE_3,
                        UiSpacing.SPACE_1, UiSpacing.SPACE_3)));
        searchButton.getAccessibleContext().setAccessibleName("搜索");
        searchButton.addActionListener(e -> search());
        Dimension comboSize = new Dimension(150, Math.max(32, statusCombo.getPreferredSize().height));
        for (JComboBox<?> combo : new JComboBox<?>[] {departmentCombo, majorCombo, classCombo, statusCombo}) {
            combo.setPreferredSize(comboSize);
            combo.setMinimumSize(new Dimension(110, comboSize.height));
        }
        keywordField.setPreferredSize(new Dimension(330, comboSize.height));
        searchButton.setPreferredSize(new Dimension(88, comboSize.height));

        addFilterLabel(bar, "关键词", 0, 0);
        addFilterControl(bar, keywordField, 1, 0, 3, 2);
        addFilterLabel(bar, "状态", 4, 0);
        addFilterControl(bar, statusCombo, 5, 0, 1, 1);
        GridBagConstraints button = constraints(6, 0);
        button.fill = GridBagConstraints.BOTH;
        button.insets = new Insets(UiSpacing.SPACE_1, UiSpacing.SPACE_2,
                UiSpacing.SPACE_1, 0);
        bar.add(searchButton, button);

        addFilterLabel(bar, "院系", 0, 1);
        addFilterControl(bar, departmentCombo, 1, 1, 1, 1);
        addFilterLabel(bar, "专业", 2, 1);
        addFilterControl(bar, majorCombo, 3, 1, 1, 1);
        addFilterLabel(bar, "班级", 4, 1);
        addFilterControl(bar, classCombo, 5, 1, 1, 1);
        GridBagConstraints filler = constraints(6, 1);
        filler.weightx = 0;
        bar.add(Box.createRigidArea(searchButton.getPreferredSize()), filler);
        return bar;
    }
}
