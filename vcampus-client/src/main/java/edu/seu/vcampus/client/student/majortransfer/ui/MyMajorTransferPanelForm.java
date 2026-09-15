package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import java.awt.*;

/** Application-form assembly for the major-transfer panel. */
abstract class MyMajorTransferPanelForm extends MyMajorTransferPanelActions {

    /** Creates the form segment of the major-transfer panel. */
    protected MyMajorTransferPanelForm(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 4);
        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        panel.add(label("目标专业:"), gbc);
        targetMajorCombo = new JComboBox<>();
        targetMajorCombo.setName("major-transfer.student.target-major");
        targetMajorCombo.addActionListener(e -> updateSubmitState());
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(targetMajorCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(label("申请类型:"), gbc);
        applicationTypeCombo = new JComboBox<>(new String[]{"普通转专业", "学困生转专业"});
        applicationTypeCombo.setName("major-transfer.student.application-type");
        applicationTypeCombo.addActionListener(e -> updateSubmitState());
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(applicationTypeCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(label("申请理由:"), gbc);
        reasonArea = new JTextArea(4, 30);
        reasonArea.setName("major-transfer.student.reason");
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setToolTipText("填写申请理由，最多2000字；修改后请先暂存再提交");
        reasonArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSubmitState(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSubmitState(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSubmitState(); }
        });
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1; gbc.weighty = 1;
        panel.add(new JScrollPane(reasonArea), gbc);

        return panel;
    }
}
