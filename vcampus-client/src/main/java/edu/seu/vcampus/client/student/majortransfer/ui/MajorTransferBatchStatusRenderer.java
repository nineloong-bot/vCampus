package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

/** Shows major-transfer batch lifecycle values as readable Chinese labels. */
final class MajorTransferBatchStatusRenderer extends DefaultListCellRenderer {
    @Override public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean selected, boolean focus) {
        super.getListCellRendererComponent(list, value, index, selected, focus);
        if (value instanceof MajorTransferBatchStatus status) setText(text(status));
        return this;
    }

    static String text(MajorTransferBatchStatus status) {
        return MajorTransferStatusText.batchStatus(status);
    }
}
