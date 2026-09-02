package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JLabel;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SwingSellerApplicationDialogTest {
    @Test
    void dialogRetainsLimitsAndMakesPendingReadOnlyButRejectedEditable() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        SwingSellerApplicationDialog owner = new SwingSellerApplicationDialog(port,
                new DefaultShopUiKit(), () -> { }, ignored -> SwingSellerApplicationDialog.CloseChoice.CANCEL);
        SellerApplicationView pending = new SellerApplicationView("a-1", "student-1", "校园店",
                "简介", "文具", "13800000000", "经营计划", SellerApplicationStatus.PENDING,
                null, null, null, null, 2);
        var readOnly = ShopSwingTestSupport.onEdt(() -> owner.createForm(Optional.of(pending), () -> { }, () -> { }));
        JTextField pendingName = ShopSwingTestSupport.component(readOnly,
                "seller.application.name", JTextField.class);
        assertThat(pendingName.isEnabled()).isFalse();

        SellerApplicationView rejected = new SellerApplicationView("a-1", "student-1", "校园店",
                "简介", "文具", "13800000000", "经营计划", SellerApplicationStatus.REJECTED,
                "请补充材料", null, null, null, 3);
        var editable = ShopSwingTestSupport.onEdt(() -> owner.createForm(Optional.of(rejected), () -> { }, () -> { }));
        JTextField name = ShopSwingTestSupport.component(editable,
                "seller.application.name", JTextField.class);
        assertThat(name.isEnabled()).isTrue();
        assertThat(ShopSwingTestSupport.component(editable,
                "seller.application.reason", JLabel.class).getText()).isEqualTo("请补充材料");
        ShopSwingTestSupport.onEdt(() -> name.setText("店".repeat(51)));
        assertThat(name.getText()).hasSize(50);
        assertThat(ShopSwingTestSupport.component(editable,
                "seller.application.name.remaining", JLabel.class).getText()).isEqualTo("还可输入 0 字");
    }

    @Test
    void saveDraftSavesOnceThenClosesAndPublishesChange() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        when(port.saveApplication(any())).thenReturn(CompletableFuture.completedFuture(draft(1)));
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger changed = new AtomicInteger();
        SwingSellerApplicationDialog owner = new SwingSellerApplicationDialog(port,
                new DefaultShopUiKit(), () -> { }, ignored -> SwingSellerApplicationDialog.CloseChoice.CANCEL);
        var form = ShopSwingTestSupport.onEdt(() -> owner.createForm(Optional.empty(),
                changed::incrementAndGet, closed::incrementAndGet));
        ShopSwingTestSupport.onEdt(() -> fill(form));

        ShopSwingTestSupport.onEdt(() -> ShopSwingTestSupport.component(form,
                "seller.application.save", JButton.class).doClick());
        ShopSwingTestSupport.flushEdt();

        verify(port).saveApplication(any());
        verify(port, never()).submitApplication(any());
        assertThat(changed).hasValue(1);
        assertThat(closed).hasValue(1);
    }

    @Test
    void directSubmitSavesThenSubmitsReturnedIdentity() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        when(port.saveApplication(any())).thenReturn(CompletableFuture.completedFuture(draft(3)));
        when(port.submitApplication(new SubmitSellerApplicationCommand("a-1", 3)))
                .thenReturn(CompletableFuture.completedFuture(draft(4)));
        SwingSellerApplicationDialog owner = new SwingSellerApplicationDialog(port,
                new DefaultShopUiKit(), () -> { }, ignored -> SwingSellerApplicationDialog.CloseChoice.CANCEL);
        var form = ShopSwingTestSupport.onEdt(() -> owner.createForm(Optional.empty(), () -> { }, () -> { }));
        ShopSwingTestSupport.onEdt(() -> fill(form));

        ShopSwingTestSupport.onEdt(() -> ShopSwingTestSupport.component(form,
                "seller.application.submit", JButton.class).doClick());
        ShopSwingTestSupport.flushEdt(); ShopSwingTestSupport.flushEdt();

        var ordered = inOrder(port);
        ordered.verify(port).saveApplication(any());
        ordered.verify(port).submitApplication(new SubmitSellerApplicationCommand("a-1", 3));
    }

    @Test
    void dirtyCloseCanCancelDiscardOrSave() throws Exception {
        for (SwingSellerApplicationDialog.CloseChoice choice : SwingSellerApplicationDialog.CloseChoice.values()) {
            SellerShopClientPort port = mock(SellerShopClientPort.class);
            when(port.saveApplication(any())).thenReturn(CompletableFuture.completedFuture(draft(2)));
            AtomicInteger closed = new AtomicInteger();
            SwingSellerApplicationDialog owner = new SwingSellerApplicationDialog(port,
                    new DefaultShopUiKit(), () -> { }, ignored -> choice);
            var form = ShopSwingTestSupport.onEdt(() -> owner.createForm(Optional.of(draft(1)),
                    () -> { }, closed::incrementAndGet));
            ShopSwingTestSupport.onEdt(() -> ShopSwingTestSupport.component(form,
                    "seller.application.name", JTextField.class).setText("修改店名"));

            ShopSwingTestSupport.onEdt(form::requestClose);
            ShopSwingTestSupport.flushEdt();

            assertThat(closed).hasValue(choice == SwingSellerApplicationDialog.CloseChoice.CANCEL ? 0 : 1);
            if (choice == SwingSellerApplicationDialog.CloseChoice.SAVE) verify(port).saveApplication(any());
            else verify(port, never()).saveApplication(any());
        }
    }

    private static void fill(java.awt.Container form) {
        ShopSwingTestSupport.component(form, "seller.application.name", JTextField.class).setText("校园店");
        ShopSwingTestSupport.component(form, "seller.application.description", JTextArea.class).setText("简介");
        ShopSwingTestSupport.component(form, "seller.application.contact", JTextField.class).setText("13800000000");
        ShopSwingTestSupport.component(form, "seller.application.statement", JTextArea.class).setText("经营计划");
    }

    private static SellerApplicationView draft(long version) {
        return new SellerApplicationView("a-1", "student-1", "校园店", "简介", "文具",
                "13800000000", "经营计划", SellerApplicationStatus.DRAFT,
                null, null, null, null, version);
    }
}
