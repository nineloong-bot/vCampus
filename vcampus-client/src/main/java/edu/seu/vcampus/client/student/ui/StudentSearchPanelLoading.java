package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.DefaultComboBoxModel;
import java.util.ArrayList;

/** Cascading department, major and class loading for the student search panel. */
abstract class StudentSearchPanelLoading extends StudentSearchPanelSelecting {

    /** Creates the loading segment of the student search panel. */
    protected StudentSearchPanelLoading(StudentClientService students, ClientConnection connection,
            boolean canEdit) {
        super(students, connection, canEdit);
    }

    void loadDepartments(long generation) {
        students.listDepartments(true).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<DepartmentView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    departmentCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                } finally {
                    suppressComboEvents = false;
                }
            } else {
                suppressComboEvents = true;
                try {
                    departmentCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
                } finally {
                    suppressComboEvents = false;
                }
            }
        }));
    }

    void cascadeLoadMajors(String departmentId, long generation) {
        suppressComboEvents = true;
        try {
            majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"正在加载..."}));
            classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
        } finally {
            suppressComboEvents = false;
        }
        if (departmentId == null) {
            suppressComboEvents = true;
            try {
                majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
            } finally {
                suppressComboEvents = false;
            }
            return;
        }
        students.listMajors(departmentId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<MajorView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    majorCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                } finally {
                    suppressComboEvents = false;
                }
            } else {
                suppressComboEvents = true;
                try {
                    majorCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
                } finally {
                    suppressComboEvents = false;
                }
            }
        }));
    }

    void cascadeLoadClasses(String majorId, long generation) {
        suppressComboEvents = true;
        try {
            classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"正在加载..."}));
        } finally {
            suppressComboEvents = false;
        }
        if (majorId == null) {
            suppressComboEvents = true;
            try {
                classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
            } finally {
                suppressComboEvents = false;
            }
            return;
        }
        students.listClasses(majorId).whenComplete((body, failure) -> onEdt(() -> {
            if (!active || generation != requestGeneration.get()) return;
            if (failure == null && body != null && body.success() && body.data() != null) {
                ArrayList<ClassView> items = body.data();
                Object[] comboItems = new Object[items.size() + 1];
                comboItems[0] = "全部";
                for (int i = 0; i < items.size(); i++) comboItems[i + 1] = items.get(i);
                suppressComboEvents = true;
                try {
                    classCombo.setModel(new DefaultComboBoxModel<>(comboItems));
                } finally {
                    suppressComboEvents = false;
                }
            } else {
                suppressComboEvents = true;
                try {
                    classCombo.setModel(new DefaultComboBoxModel<>(new Object[]{"全部"}));
                } finally {
                    suppressComboEvents = false;
                }
            }
        }));
    }
}
