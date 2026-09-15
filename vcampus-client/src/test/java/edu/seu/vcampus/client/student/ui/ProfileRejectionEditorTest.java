package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileRejectionEditorTest {
    @Test void requiresAReasonAndSubmitsItInline() {
        AtomicReference<String> submitted = new AtomicReference<>();
        ProfileRejectionEditor editor = new ProfileRejectionEditor(submitted::set, () -> { });
        JTextArea reason = find(editor.component(), JTextArea.class);
        JButton confirm = find(editor.component(), "student.profile.review.confirmReject", JButton.class);

        confirm.doClick();
        assertThat(submitted).hasValue(null);
        reason.setText("证件日期需要补充");
        confirm.doClick();

        assertThat(submitted).hasValue("证件日期需要补充");
        assertThat(editor.size()).isEqualTo(EditorSize.COMPACT);
        assertThat(editor.isDirty()).isTrue();
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, name, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
