package edu.seu.vcampus.client.student.majortransfer;

import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferBatchChoiceRenderer;
import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferCollegeWorkspaceView;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView;
import java.time.Instant;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MajorTransferCollegeWorkspaceTest {
    @Test void batchRendererAndWorkspaceHideRecordDiagnosticsAndKeepBothCardsVisible() {
        var batch = new MajorTransferBatchView("b1", "2026秋季转专业", MajorTransferBatchStatus.OPEN,
                Instant.EPOCH, Instant.EPOCH, null, null, Instant.EPOCH, 0);
        JComboBox<MajorTransferBatchView> combo = new JComboBox<>(new MajorTransferBatchView[]{batch});
        combo.setRenderer(new MajorTransferBatchChoiceRenderer());
        var rendered = (javax.swing.JLabel) combo.getRenderer().getListCellRendererComponent(
                new JList<>(), batch, 0, false, false);
        assertThat(rendered.getText()).isEqualTo("2026秋季转专业");

        JTextArea detail = new JTextArea();
        JPanel workspace = new MajorTransferCollegeWorkspaceView(new JList<>(), detail, new JPanel());
        assertThat(detail.getText()).contains("请选择");
        assertThat(workspace.getComponentCount()).isEqualTo(1);
    }
}
