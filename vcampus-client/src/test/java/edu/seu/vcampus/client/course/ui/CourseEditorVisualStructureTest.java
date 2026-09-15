package edu.seu.vcampus.client.course.ui;

import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CourseEditorVisualStructureTest {
    @Test void cardHasThemeBorderAndNamedActionArea() {
        JPanel actions = new JPanel();
        JPanel card = CourseEditorCard.create(new JLabel("内容"), actions);

        assertThat(card.getName()).isEqualTo("course-editor-card");
        assertThat(card.getBackground()).isNotEqualTo(Color.WHITE);
        assertThat(card.getBorder()).isNotNull();
        assertThat(actions.getName()).isEqualTo("course-editor-actions");
        assertThat(new OfferingScheduleEditorPanel().getBorder()).isNotNull();
    }
}
