package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView;
import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

/** Renders transfer batches as concise labels instead of record diagnostics. */
public final class MajorTransferBatchChoiceRenderer extends DefaultListCellRenderer {
    @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                             boolean selected, boolean focus) {
        super.getListCellRendererComponent(list, value, index, selected, focus);
        if (value instanceof MajorTransferBatchView batch) setText(batch.batchName());
        return this;
    }
}
