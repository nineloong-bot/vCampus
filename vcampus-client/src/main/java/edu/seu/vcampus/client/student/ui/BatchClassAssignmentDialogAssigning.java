package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;

import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Gender-balanced snake-round class distribution for the batch assignment segments. */
abstract class BatchClassAssignmentDialogAssigning extends BatchClassAssignmentDialogFile {

    BatchClassAssignmentDialogAssigning(Window owner, StudentClientService students,
            MajorView major, List<ClassView> classes) {
        super(owner, students, major, classes);
    }

    void autoAssign() {
        if (rows.isEmpty() || availableClasses.size() < 2) return;
        int n = availableClasses.size();
        List<StudentRow> males = new ArrayList<>();
        List<StudentRow> females = new ArrayList<>();
        for (StudentRow r : rows) {
            if ("男".equals(r.gender)) males.add(r); else females.add(r);
        }
        males.sort(Comparator.comparingDouble((StudentRow r) -> r.score).reversed());
        females.sort(Comparator.comparingDouble((StudentRow r) -> r.score).reversed());
        // Snake-round assignment within each gender group
        assignSnakeRound(males, n);
        assignSnakeRound(females, n);
        tableModel.fireTableDataChanged();
        updateStats();
        importButton.setEnabled(true);
        errorLabel.setText(" ");
    }

    private static void assignSnakeRound(List<StudentRow> group, int classCount) {
        boolean forward = true;
        int idx = 0;
        for (StudentRow row : group) {
            row.classIndex = forward ? idx : (classCount - 1 - idx);
            if (forward) {
                idx++;
                if (idx >= classCount) { idx = classCount - 1; forward = false; }
            } else {
                idx--;
                if (idx < 0) { idx = 0; forward = true; }
            }
        }
    }
}
