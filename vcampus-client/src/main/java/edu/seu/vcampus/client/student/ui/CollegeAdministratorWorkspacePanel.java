package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministratorView;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Dedicated operation workspace panel for assigning and transferring college administrators.
 */
public final class CollegeAdministratorWorkspacePanel extends JPanel {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final CollegeAdministratorAssignCard assignCard;
    private final CollegeAdministratorTransferCard transferCard;

    private List<DepartmentView> departments = List.of();
    private StudentCollegeAdministratorView currentAdmin;

    /**
     * Constructs the workspace panel with the required assignment, transfer, and deactivation actions.
     *
     * @param onAssign callback when confirming administrator assignment
     * @param onTransfer callback when confirming administrator transfer
     * @param onDeactivate callback when deactivating an administrator
     */
    public CollegeAdministratorWorkspacePanel(
            BiConsumer<StudentCollegeAdministratorView, DepartmentView> onAssign,
            BiConsumer<StudentCollegeAdministratorView, DepartmentView> onTransfer,
            Consumer<StudentCollegeAdministratorView> onDeactivate) {
        super(new BorderLayout());
        Objects.requireNonNull(onAssign, "onAssign");
        Objects.requireNonNull(onTransfer, "onTransfer");
        Objects.requireNonNull(onDeactivate, "onDeactivate");

        setName("collegeAdminWorkspacePanel");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(new CompoundBorder(
                new TitledBorder("操作工作区"),
                new EmptyBorder(UiSpacing.SPACE_3, UiSpacing.SPACE_3, UiSpacing.SPACE_3, UiSpacing.SPACE_3)));

        this.assignCard = new CollegeAdministratorAssignCard(onAssign);
        this.transferCard = new CollegeAdministratorTransferCard(onTransfer, onDeactivate);

        cards.setOpaque(false);
        cards.add(buildEmptyCard(), "EMPTY");
        cards.add(assignCard, "ASSIGN");
        cards.add(transferCard, "TRANSFER");

        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, "EMPTY");
    }

    private JPanel buildEmptyCard() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        JLabel hint = new JLabel("<html><center style='color:#6D6556;'>工作区就绪<br><br>"
                + "请在左侧列表中选择学院管理员<br>以进行分配或调动操作</center></html>", SwingConstants.CENTER);
        hint.setFont(UiTypography.BODY);
        panel.add(hint);
        return panel;
    }

    /**
     * Updates the assignable active departments.
     *
     * @param depts the list of active departments
     */
    public void setDepartments(List<DepartmentView> depts) {
        this.departments = depts == null ? List.of() : List.copyOf(depts);
        if (currentAdmin != null) {
            showAdministrator(currentAdmin);
        }
    }

    /**
     * Shows the specified administrator in the workspace.
     *
     * @param admin the administrator to manage, or null to clear the workspace
     */
    public void showAdministrator(StudentCollegeAdministratorView admin) {
        this.currentAdmin = admin;
        if (admin == null) {
            cardLayout.show(cards, "EMPTY");
            return;
        }
        if (admin.assigned()) {
            showTransfer();
        } else {
            showAssign();
        }
    }

    /** Switches the workspace to the assignment card for the current administrator. */
    public void showAssign() {
        if (currentAdmin == null) return;
        assignCard.display(currentAdmin, departments);
        cardLayout.show(cards, "ASSIGN");
    }

    /** Switches the workspace to the transfer card for the current administrator. */
    public void showTransfer() {
        if (currentAdmin == null) return;
        transferCard.display(currentAdmin, departments);
        cardLayout.show(cards, "TRANSFER");
    }

    /** Requests focus on the active department selector combo box. */
    public void focusChoice() {
        if (currentAdmin == null) return;
        if (currentAdmin.assigned()) {
            transferCard.focusChoice();
        } else {
            assignCard.focusChoice();
        }
    }
}
