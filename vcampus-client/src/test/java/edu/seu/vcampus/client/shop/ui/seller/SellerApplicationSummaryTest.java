package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Component;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerApplicationSummaryTest {
    @Test
    void summaryLoadsStatusAndOpensApplicationDialogWithoutEmbeddingEditableFields() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        SellerApplicationView draft = application(SellerApplicationStatus.DRAFT, null);
        when(port.getMyApplication()).thenReturn(CompletableFuture.completedFuture(Optional.of(draft)));
        RecordingDialog dialog = new RecordingDialog();
        SellerApplicationPanel panel = ShopSwingTestSupport.onEdt(() ->
                new SellerApplicationPanel(port, new DefaultShopUiKit(), () -> { }, dialog));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();

        assertThat(ShopSwingTestSupport.component(panel,
                "seller.application.status", JLabel.class).getText()).isEqualTo("草稿");
        assertThat(find(panel, "seller.application.name")).isNull();
        JButton edit = ShopSwingTestSupport.component(panel,
                "seller.application.edit", JButton.class);
        assertThat(edit.getText()).isEqualTo("修改申请");
        ShopSwingTestSupport.onEdt(() -> edit.doClick());
        assertThat(dialog.opened).hasValue(1);
        assertThat(dialog.application).contains(draft);
    }

    @Test
    void refreshIsDisabledUntilLatestRequestCompletesAndReloadsOnce() throws Exception {
        SellerShopClientPort port = mock(SellerShopClientPort.class);
        CompletableFuture<Optional<SellerApplicationView>> first = new CompletableFuture<>();
        CompletableFuture<Optional<SellerApplicationView>> second = new CompletableFuture<>();
        when(port.getMyApplication()).thenReturn(first, second);
        SellerApplicationPanel panel = ShopSwingTestSupport.onEdt(() ->
                new SellerApplicationPanel(port, new DefaultShopUiKit(), () -> { },
                        new RecordingDialog()));

        ShopSwingTestSupport.onEdt(panel::load);
        JButton refresh = ShopSwingTestSupport.component(panel,
                "seller.application.refresh", JButton.class);
        assertThat(refresh.isEnabled()).isFalse();
        first.complete(Optional.empty());
        ShopSwingTestSupport.flushEdt();
        assertThat(refresh.isEnabled()).isTrue();

        ShopSwingTestSupport.onEdt(() -> refresh.doClick());
        assertThat(refresh.isEnabled()).isFalse();
        verify(port, times(2)).getMyApplication();
        second.complete(Optional.of(application(SellerApplicationStatus.REJECTED, "请补充材料")));
        ShopSwingTestSupport.flushEdt();
        assertThat(refresh.isEnabled()).isTrue();
        assertThat(ShopSwingTestSupport.component(panel,
                "seller.application.reason", JLabel.class).getText()).isEqualTo("请补充材料");
    }

    private static SellerApplicationView application(SellerApplicationStatus status, String reason) {
        return new SellerApplicationView("a-1", "student-1", "校园店", "简介", "文具",
                "13800000000", "经营计划", status, reason, null,
                Instant.EPOCH, null, 2);
    }

    private static Component find(java.awt.Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof java.awt.Container nested) {
                Component found = find(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class RecordingDialog implements SellerApplicationDialogPort {
        private final AtomicInteger opened = new AtomicInteger();
        private Optional<SellerApplicationView> application = Optional.empty();

        @Override
        public void open(Component parent, Optional<SellerApplicationView> application,
                Runnable changed) {
            opened.incrementAndGet();
            this.application = application;
        }
    }
}
