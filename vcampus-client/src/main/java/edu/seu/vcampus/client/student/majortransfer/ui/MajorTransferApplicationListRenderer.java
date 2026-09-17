package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationView;

import javax.swing.*;
import java.awt.Component;

/** Renders major-transfer applications in the college review list. */
final class MajorTransferApplicationListRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean selected, boolean focus) {
        super.getListCellRendererComponent(list, value, index, selected, focus);
        if (value instanceof MajorTransferApplicationView app) {
            String idTag = app.campusCardNumber() != null && !app.campusCardNumber().isBlank()
                    ? app.campusCardNumber() : app.fromStudentNumber();
            String studentDisplay = app.studentName()
                    + (idTag != null && !idTag.isBlank() ? " (" + idTag + ")" : "");
            setText(studentDisplay + "  " + app.fromMajorName() + " → "
                    + app.targetMajorName() + "  [" + MajorTransferStatusText.status(app.status()) + "]");
        }
        return this;
    }
}
