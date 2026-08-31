package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationReviewPanelTest {
    @Test
    void loadsPendingApplicationAndApprovesSelectedVersion() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        SellerApplicationView pending = new SellerApplicationView("application-1", "student-1",
                "文具店", "简介", "文具", "contact", "经营计划",
                SellerApplicationStatus.PENDING, null, null, Instant.EPOCH, null, 4);
        when(port.searchApplications(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(pending), 0, 50, 1)));
        when(port.reviewApplication(any())).thenReturn(CompletableFuture.completedFuture(pending));
        ApplicationReviewPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ApplicationReviewPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTable table = ShopSwingTestSupport.component(panel, "admin.applications.table", JTable.class);
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 2)).isEqualTo("文具店");
        ShopSwingTestSupport.onEdt(() -> {
            table.setRowSelectionInterval(0, 0);
            ShopSwingTestSupport.component(panel, "admin.applications.approve", JButton.class).doClick();
        });
        ShopSwingTestSupport.flushEdt();

        verify(port).reviewApplication(new ReviewSellerApplicationCommand("application-1",
                SellerReviewDecision.APPROVE, null, 4));
    }
}
