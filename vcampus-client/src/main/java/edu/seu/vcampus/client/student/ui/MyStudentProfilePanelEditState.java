package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.StudentPersonalProfile;
import edu.seu.vcampus.common.student.StudentProfileApplicationStatus;
import edu.seu.vcampus.common.student.StudentProfileApplicationView;
import edu.seu.vcampus.common.student.StudentView;

import javax.swing.*;
import java.time.LocalDate;

/** Edit-state transitions and edit-field population for the profile panel. */
abstract class MyStudentProfilePanelEditState extends MyStudentProfilePanelBase {

    /** Creates the edit-state segment of the profile panel. */
    protected MyStudentProfilePanelEditState(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    void startPersonalEdit() {
        isPersonalEditing = true;
        personalEdit.setText("取消编辑");
        personalSave.setVisible(true);
        populatePersonalEditFields();
        personalCardLayout.show(personalCardContainer, "EDIT");
        errorLabel.setText(" ");
    }

    void cancelPersonalEdit() {
        isPersonalEditing = false;
        personalEdit.setText("编辑");
        personalSave.setVisible(false);
        personalCardLayout.show(personalCardContainer, "VIEW");
        errorLabel.setText(" ");
    }

    void populatePersonalEditFields() {
        if (workspace == null) return;
        StudentProfileApplicationView app = workspace.application();
        boolean draftVisible = app != null && (app.status() == StudentProfileApplicationStatus.DRAFT
                || app.status() == StudentProfileApplicationStatus.PENDING);
        StudentPersonalProfile p = draftVisible ? app.personal() : workspace.formalProfile().personal();
        StudentView core = workspace.formalProfile().core();

        setEditLabel("card", core.campusCardNumber());
        setEditLabel("studentNumber", core.studentNumber());
        setEditLabel("name", core.studentName());
        setEditLabel("gender", core.gender());
        setEditLabel("birthDate", p.birthDate());
        setEditLabel("idDocumentType", p.idDocumentType());
        setEditLabel("idDocumentNumber", p.idDocumentNumber());

        setEditValue("namePinyin", p.namePinyin());
        setEditValue("formerName", p.formerName());
        setEditValue("politicalStatus", p.politicalStatus());
        setEditValue("ethnicity", p.ethnicity());
        setEditValue("maritalStatus", p.maritalStatus());
        setEditValue("idIssuedDate", p.idIssuedDate());
        setEditValue("nativePlace", p.nativePlace());
        setEditValue("countryRegion", p.countryRegion());
        setEditValue("birthplace", p.birthplace());
        setEditValue("studentOriginPlace", p.studentOriginPlace());
        setEditValue("householdRegistrationType", p.householdRegistrationType());
        setEditValue("householdBeforeEnrollment", p.householdBeforeEnrollment());
        setEditValue("householdAfterEnrollment", p.householdAfterEnrollment());
        setEditValue("overseasChineseStatus", p.overseasChineseStatus());
        setEditValue("religion", p.religion());
        setEditValue("leagueMember", p.leagueMember() ? "是" : "否");
        setEditValue("leagueJoinDate", p.leagueJoinDate());
        setEditValue("partyMember", p.partyMember() ? "是" : "否");
        setEditValue("partyJoinDate", p.partyJoinDate());
        setEditValue("healthStatus", p.healthStatus());
        setEditValue("bloodType", p.bloodType());
        setEditValue("weightKg", p.weightKg());
        setEditValue("heightCm", p.heightCm());
        setEditValue("specialties", p.specialties());
        setEditValue("hobbies", p.hobbies());
        setEditValue("onlyChild", p.onlyChild() ? "是" : "否");
        setEditValue("email", p.email());
        setEditValue("phone", p.phone());

        boolean isMasses = "群众".equals(p.politicalStatus());
        JTextField legDate = (JTextField) editComponents.get("leagueJoinDate");
        if (legDate != null) legDate.setEnabled(!isMasses && p.leagueMember());
        JTextField ptyDate = (JTextField) editComponents.get("partyJoinDate");
        if (ptyDate != null) ptyDate.setEnabled(!isMasses && p.partyMember());
    }

    void setEditLabel(String key, Object value) {
        JLabel label = editReadOnlyLabels.get(key);
        if (label != null) {
            String text = filled(value);
            label.setText(text);
            label.setToolTipText(text);
        }
    }

    void setEditValue(String key, Object value) {
        JComponent comp = editComponents.get(key);
        if (comp == null) return;
        String valStr = value != null ? value.toString().trim() : "";
        if (comp instanceof JTextField tf) {
            tf.setText(valStr);
        } else if (comp instanceof JComboBox<?> raw) {
            @SuppressWarnings("unchecked")
            JComboBox<String> combo = (JComboBox<String>) raw;
            if (!valStr.isEmpty()) {
                boolean found = false;
                for (int i = 0; i < combo.getItemCount(); i++) {
                    if (valStr.equals(combo.getItemAt(i))) {
                        found = true;
                        break;
                    }
                }
                if (!found) combo.addItem(valStr);
                combo.setSelectedItem(valStr);
            } else {
                combo.setSelectedIndex(0);
            }
        }
    }

    String editVal(String key) {
        JComponent comp = editComponents.get(key);
        if (comp instanceof JTextField tf) {
            String t = tf.getText().trim();
            return t.isEmpty() ? null : t;
        } else if (comp instanceof JComboBox<?> combo) {
            Object sel = combo.getSelectedItem();
            if (sel == null) return null;
            String t = sel.toString().trim();
            if (t.isEmpty() || "请选择...".equals(t)) return null;
            return t;
        }
        return null;
    }

    LocalDate editDate(String key) {
        String val = editVal(key);
        return val == null ? null : LocalDate.parse(val);
    }

    Integer editInt(String key) {
        String val = editVal(key);
        return val == null ? null : Integer.valueOf(val);
    }
}
