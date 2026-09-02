package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class SellerApplicationPanelTest {
    @Test
    void completeNewApplicationSavesLatestFieldsThenSubmitsReturnedVersion() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        when(port.getMyApplication()).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(port.saveApplication(any())).thenReturn(CompletableFuture.completedFuture(savedDraft(3)));
        when(port.submitApplication(new SubmitSellerApplicationCommand("a-1", 3)))
                .thenReturn(CompletableFuture.completedFuture(pending(4)));
        SellerApplicationPanel panel = panel(port, choice -> SellerApplicationPanel.LeaveChoice.CANCEL);
        ShopSwingTestSupport.onEdt(panel::load); ShopSwingTestSupport.flushEdt();
        ShopSwingTestSupport.onEdt(() -> fillValidForm(panel));

        ShopSwingTestSupport.onEdt(() -> ShopSwingTestSupport.component(panel,
                "seller.application.submit", JButton.class).doClick());
        ShopSwingTestSupport.flushEdt(); ShopSwingTestSupport.flushEdt();

        var order = inOrder(port);
        order.verify(port).saveApplication(argThat(command -> command.applicationId() == null
                && command.shopName().equals("东南校园店")));
        order.verify(port).submitApplication(new SubmitSellerApplicationCommand("a-1", 3));
    }

    @Test
    void dirtyLeaveChoicesSaveDiscardOrCancel() throws Exception {
        for (SellerApplicationPanel.LeaveChoice choice : SellerApplicationPanel.LeaveChoice.values()) {
            SellerShopClientPort port = mock(SellerShopClientPort.class);
            when(port.getMyApplication()).thenReturn(CompletableFuture.completedFuture(Optional.of(savedDraft(2))));
            CompletableFuture<SellerApplicationView> save = new CompletableFuture<>();
            when(port.saveApplication(any())).thenReturn(save);
            AtomicInteger prompts = new AtomicInteger();
            SellerApplicationPanel panel = panel(port, ignored -> { prompts.incrementAndGet(); return choice; });
            ShopSwingTestSupport.onEdt(panel::load); ShopSwingTestSupport.flushEdt();
            ShopSwingTestSupport.onEdt(() -> ShopSwingTestSupport.component(panel,
                    "seller.application.name", JTextField.class).setText("修改后店名"));
            AtomicInteger proceeded = new AtomicInteger();

            ShopSwingTestSupport.onEdt(() -> panel.requestLeave(proceeded::incrementAndGet));

            assertThat(prompts).hasValue(1);
            if (choice == SellerApplicationPanel.LeaveChoice.SAVE) {
                assertThat(proceeded).hasValue(0);
                save.complete(savedDraft(3)); ShopSwingTestSupport.flushEdt();
                assertThat(proceeded).hasValue(1);
                verify(port).saveApplication(any());
            } else {
                assertThat(proceeded).hasValue(choice == SellerApplicationPanel.LeaveChoice.DISCARD ? 1 : 0);
                verify(port, never()).saveApplication(any());
            }
        }
    }

    @Test
    void unchangedLoadedDraftLeavesWithoutPrompt() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        when(port.getMyApplication()).thenReturn(CompletableFuture.completedFuture(Optional.of(savedDraft(2))));
        AtomicInteger prompts = new AtomicInteger();
        SellerApplicationPanel panel = panel(port, ignored -> { prompts.incrementAndGet(); return SellerApplicationPanel.LeaveChoice.CANCEL; });
        ShopSwingTestSupport.onEdt(panel::load); ShopSwingTestSupport.flushEdt();
        AtomicInteger proceeded = new AtomicInteger();

        ShopSwingTestSupport.onEdt(() -> panel.requestLeave(proceeded::incrementAndGet));

        assertThat(prompts).hasValue(0);
        assertThat(proceeded).hasValue(1);
    }

    @Test
    void applicationAndWorkspacesHaveDedicatedRoutes() {
        assertThat(new ShopRoute.SellerApplication()).isInstanceOf(ShopRoute.class);
        assertThat(new ShopRoute.SellerWorkspace()).isInstanceOf(ShopRoute.class);
        assertThat(new ShopRoute.AdminWorkspace()).isInstanceOf(ShopRoute.class);
    }

    @Test
    void rejectedApplicationKeepsIdentityAndShowsReasonForEditing() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        SellerApplicationView rejected = new SellerApplicationView("application-1", "student-1",
                "旧店名", "简介", "文具", "contact", "经营计划",
                SellerApplicationStatus.REJECTED, "请补充说明", "admin-1",
                Instant.EPOCH, Instant.EPOCH, 3);
        when(port.getMyApplication()).thenReturn(CompletableFuture.completedFuture(Optional.of(rejected)));
        SellerApplicationPanel panel = ShopSwingTestSupport.onEdt(() ->
                new SellerApplicationPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();

        assertThat(ShopSwingTestSupport.component(panel, "seller.application.reason", JLabel.class)
                .getText()).contains("请补充说明");
        assertThat(ShopSwingTestSupport.component(panel, "seller.application.name", JTextField.class)
                .isEnabled()).isTrue();
        assertThat(ShopSwingTestSupport.component(panel, "seller.application.save", JButton.class)
                .isEnabled()).isTrue();
    }

    @Test
    void pendingApplicationIsReadOnly() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        SellerApplicationView pending = new SellerApplicationView("application-1", "student-1",
                "店名", "简介", "文具", "contact", "经营计划",
                SellerApplicationStatus.PENDING, null, null, Instant.EPOCH, null, 2);
        when(port.getMyApplication()).thenReturn(CompletableFuture.completedFuture(Optional.of(pending)));
        SellerApplicationPanel panel = ShopSwingTestSupport.onEdt(() ->
                new SellerApplicationPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();

        assertThat(ShopSwingTestSupport.component(panel, "seller.application.name", JTextField.class)
                .isEnabled()).isFalse();
        assertThat(ShopSwingTestSupport.component(panel, "seller.application.submit", JButton.class)
                .isEnabled()).isFalse();
    }

    @Test
    void limitedFieldsShowGuidanceAndPreventPastedTextBeyondTheRemainingCount() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        SellerApplicationPanel panel = panel(port,
                ignored -> SellerApplicationPanel.LeaveChoice.CANCEL);
        JTextField name = ShopSwingTestSupport.component(panel,
                "seller.application.name", JTextField.class);
        JTextField contact = ShopSwingTestSupport.component(panel,
                "seller.application.contact", JTextField.class);
        JTextArea statement = ShopSwingTestSupport.component(panel,
                "seller.application.statement", JTextArea.class);

        assertThat(name.getText()).isEmpty();
        assertThat(name.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("店铺名称不能超过 50 字");
        assertThat(ShopSwingTestSupport.component(panel,
                "seller.application.name.remaining", JLabel.class).getText()).isEqualTo("还可输入 50 字");
        assertThat(ShopSwingTestSupport.component(panel,
                "seller.application.contact.remaining", JLabel.class).getText()).isEqualTo("还可输入 50 字");
        assertThat(ShopSwingTestSupport.component(panel,
                "seller.application.statement.remaining", JLabel.class).getText()).isEqualTo("还可输入 500 字");

        ShopSwingTestSupport.onEdt(() -> {
            name.setText("店".repeat(51));
            contact.setText("联".repeat(51));
            statement.setText("计".repeat(501));
        });

        assertThat(name.getText()).hasSize(50);
        assertThat(contact.getText()).hasSize(50);
        assertThat(statement.getText()).hasSize(500);
        assertThat(ShopSwingTestSupport.component(panel,
                "seller.application.name.remaining", JLabel.class).getText()).isEqualTo("还可输入 0 字");
        assertThat(ShopSwingTestSupport.component(panel,
                "seller.application.statement.remaining", JLabel.class).getText()).isEqualTo("还可输入 0 字");
    }

    private static SellerApplicationPanel panel(SellerShopClientPort port,
            SellerApplicationPanel.LeavePrompt prompt) throws Exception {
        return ShopSwingTestSupport.onEdt(() -> new SellerApplicationPanel(
                port, new DefaultShopUiKit(), () -> { }, prompt));
    }

    private static void fillValidForm(SellerApplicationPanel panel) {
        ShopSwingTestSupport.component(panel, "seller.application.name", JTextField.class).setText("东南校园店");
        ShopSwingTestSupport.component(panel, "seller.application.description", JTextArea.class).setText("校园用品");
        ShopSwingTestSupport.component(panel, "seller.application.contact", JTextField.class).setText("13800000000");
        ShopSwingTestSupport.component(panel, "seller.application.statement", JTextArea.class).setText("稳定经营");
    }

    private static SellerApplicationView savedDraft(long version) {
        return new SellerApplicationView("a-1", "student-1", "东南校园店", "校园用品", "文具",
                "13800000000", "稳定经营", SellerApplicationStatus.DRAFT,
                null, null, null, null, version);
    }

    private static SellerApplicationView pending(long version) {
        SellerApplicationView draft = savedDraft(version);
        return new SellerApplicationView("a-1", draft.applicantUserId(), draft.shopName(),
                draft.description(), draft.category(), draft.contact(), draft.applicationStatement(),
                SellerApplicationStatus.PENDING, null, null, Instant.EPOCH, null, version);
    }
}
