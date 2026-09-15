package edu.seu.vcampus.client.course.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;

import javax.swing.JSpinner;

import org.junit.jupiter.api.Test;

class OfferingEditorRetakeDefaultTest {
    @Test
    void newOfferingStartsWithFiveRetakeSeatsIndependentOfNormalCapacity() {
        OfferingEditorPanel editor = new OfferingEditorPanel(
                CourseUiGateway.preview(), null, () -> { }, () -> { });
        JSpinner normal = spinner(editor.component(), "容量");
        JSpinner retake = spinner(editor.component(), "重修容量");

        assertThat(retake.getValue()).isEqualTo(5);
        normal.setValue(80);
        assertThat(retake.getValue()).isEqualTo(5);
        editor.onClosed();
    }

    private static JSpinner spinner(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (child instanceof JSpinner value
                    && name.equals(value.getAccessibleContext().getAccessibleName())) return value;
            if (child instanceof Container container) {
                JSpinner match = spinnerOrNull(container, name);
                if (match != null) return match;
            }
        }
        throw new AssertionError("Missing spinner " + name);
    }

    private static JSpinner spinnerOrNull(Container root, String name) {
        try { return spinner(root, name); }
        catch (AssertionError missing) { return null; }
    }
}
