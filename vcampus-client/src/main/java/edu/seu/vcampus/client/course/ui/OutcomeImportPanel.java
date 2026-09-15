package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/** Administrator entry page for pass/fail course-outcome imports. */
public final class OutcomeImportPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final EmbeddedEditorHost editorHost;

    /** Creates the outcome-import page with a hidden editor workspace. */
    public OutcomeImportPanel(CourseUiGateway gateway) {
        super("课程结果导入");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        JPanel entry = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SM, 0));
        entry.setOpaque(false);
        JButton open = primary("导入课程结果");
        open.addActionListener(event -> openEditor());
        entry.add(open);
        editorHost = new EmbeddedEditorHost(entry);
        body.add(editorHost, BorderLayout.CENTER);
    }

    private void openEditor() {
        editorHost.showEditor((complete, cancel) ->
                new OutcomeImportEditorPanel(gateway, complete, cancel));
    }
}
