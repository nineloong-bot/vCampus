package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationReviewPanelTest {
    @Test
    void refreshReloadsBothListsAndStaysDisabledUntilBothComplete() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        CompletableFuture<PageResult<SellerApplicationView>> pendingFirst = new CompletableFuture<>();
        CompletableFuture<PageResult<SellerApplicationView>> processedFirst = new CompletableFuture<>();
        CompletableFuture<PageResult<SellerApplicationView>> pendingSecond = new CompletableFuture<>();
        CompletableFuture<PageResult<SellerApplicationView>> processedSecond = new CompletableFuture<>();
        when(port.searchApplications(argThat(query -> query != null && query.mode() == SellerApplicationListMode.PENDING)))
                .thenReturn(pendingFirst, pendingSecond);
        when(port.searchApplications(argThat(query -> query != null && query.mode() == SellerApplicationListMode.PROCESSED)))
                .thenReturn(processedFirst, processedSecond);
        ApplicationReviewPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ApplicationReviewPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        JButton refresh = ShopSwingTestSupport.component(panel,
                "admin.applications.refresh", JButton.class);
        assertThat(refresh.isEnabled()).isFalse();
        pendingFirst.complete(new PageResult<>(List.of(), 0, 50, 0));
        ShopSwingTestSupport.flushEdt();
        assertThat(refresh.isEnabled()).isFalse();
        processedFirst.complete(new PageResult<>(List.of(), 0, 50, 0));
        ShopSwingTestSupport.flushEdt();
        assertThat(refresh.isEnabled()).isTrue();

        ShopSwingTestSupport.onEdt(() -> refresh.doClick());
        assertThat(refresh.isEnabled()).isFalse();
        pendingSecond.complete(new PageResult<>(List.of(), 0, 50, 0));
        ShopSwingTestSupport.flushEdt();
        assertThat(refresh.isEnabled()).isFalse();
        processedSecond.complete(new PageResult<>(List.of(), 0, 50, 0));
        ShopSwingTestSupport.flushEdt();
        assertThat(refresh.isEnabled()).isTrue();
        verify(port, times(4)).searchApplications(any());
    }

    @Test
    void loadsPendingApplicationAndApprovesSelectedVersion() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        SellerApplicationView pending = new SellerApplicationView("application-1", "student-1",
                "文具店", "简介", "文具", "contact", "经营计划",
                SellerApplicationStatus.PENDING, null, null, Instant.EPOCH, null, 4);
        when(port.searchApplications(argThat(query -> query != null && query.mode() == SellerApplicationListMode.PENDING)))
                .thenReturn(CompletableFuture.completedFuture(new PageResult<>(List.of(pending), 0, 50, 1)));
        when(port.searchApplications(argThat(query -> query != null && query.mode() == SellerApplicationListMode.PROCESSED)))
                .thenReturn(CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 50, 0)));
        when(port.reviewApplication(any())).thenReturn(CompletableFuture.completedFuture(pending));
        ApplicationReviewPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ApplicationReviewPanel(port, new DefaultShopUiKit(), () -> { }));

        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTabbedPane tabs = ShopSwingTestSupport.component(panel, "admin.applications.tabs", JTabbedPane.class);
        assertThat(tabs.getSelectedIndex()).isZero();
        JTable table = ShopSwingTestSupport.component(panel, "admin.applications.pending", JTable.class);
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

    @Test
    void onlyPendingSelectionEnablesReviewActions() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        SellerApplicationView pending = application("pending", SellerApplicationStatus.PENDING, 4);
        SellerApplicationView processed = application("processed", SellerApplicationStatus.APPROVED, 5);
        when(port.searchApplications(argThat(query -> query != null
                && query.mode() == SellerApplicationListMode.PENDING)))
                .thenReturn(CompletableFuture.completedFuture(
                        new PageResult<>(List.of(pending), 0, 50, 1)));
        when(port.searchApplications(argThat(query -> query != null
                && query.mode() == SellerApplicationListMode.PROCESSED)))
                .thenReturn(CompletableFuture.completedFuture(
                        new PageResult<>(List.of(processed), 0, 50, 1)));
        ApplicationReviewPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ApplicationReviewPanel(port, new DefaultShopUiKit(), () -> { }));
        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTable pendingTable = ShopSwingTestSupport.component(panel,
                "admin.applications.pending", JTable.class);
        JTable processedTable = ShopSwingTestSupport.component(panel,
                "admin.applications.processed", JTable.class);
        JButton approve = ShopSwingTestSupport.component(panel,
                "admin.applications.approve", JButton.class);
        JButton reject = ShopSwingTestSupport.component(panel,
                "admin.applications.reject", JButton.class);

        assertThat(approve.isEnabled()).isFalse();
        assertThat(reject.isEnabled()).isFalse();
        ShopSwingTestSupport.onEdt(() -> pendingTable.setRowSelectionInterval(0, 0));
        assertThat(approve.isEnabled()).isTrue();
        assertThat(reject.isEnabled()).isTrue();

        ShopSwingTestSupport.onEdt(() -> processedTable.setRowSelectionInterval(0, 0));
        assertThat(pendingTable.getSelectedRow()).isEqualTo(-1);
        assertThat(approve.isEnabled()).isFalse();
        assertThat(reject.isEnabled()).isFalse();
    }

    @Test
    void doubleClickOpensPendingAsReviewableAndProcessedAsReadOnly() throws Exception {
        AdminShopClientPort port = mock(AdminShopClientPort.class);
        SellerApplicationView pending = application("pending", SellerApplicationStatus.PENDING, 4);
        SellerApplicationView processed = application("processed", SellerApplicationStatus.APPROVED, 5);
        when(port.searchApplications(argThat(query -> query != null
                && query.mode() == SellerApplicationListMode.PENDING)))
                .thenReturn(CompletableFuture.completedFuture(
                        new PageResult<>(List.of(pending), 0, 50, 1)));
        when(port.searchApplications(argThat(query -> query != null
                && query.mode() == SellerApplicationListMode.PROCESSED)))
                .thenReturn(CompletableFuture.completedFuture(
                        new PageResult<>(List.of(processed), 0, 50, 1)));
        when(port.reviewApplication(any())).thenReturn(CompletableFuture.completedFuture(pending));
        java.util.List<String> opened = new java.util.ArrayList<>();
        ApplicationReviewPanel.DetailDialog dialogs = (parent, application, reviewable) -> {
            opened.add(application.applicationId() + ":" + reviewable);
            return reviewable
                    ? java.util.Optional.of(new ApplicationReviewPanel.DetailReview(
                            SellerReviewDecision.APPROVE, null))
                    : java.util.Optional.empty();
        };
        ApplicationReviewPanel panel = ShopSwingTestSupport.onEdt(() ->
                new ApplicationReviewPanel(port, new DefaultShopUiKit(), () -> { }, dialogs));
        ShopSwingTestSupport.onEdt(panel::load);
        ShopSwingTestSupport.flushEdt();
        JTable pendingTable = ShopSwingTestSupport.component(panel,
                "admin.applications.pending", JTable.class);
        JTable processedTable = ShopSwingTestSupport.component(panel,
                "admin.applications.processed", JTable.class);

        ShopSwingTestSupport.onEdt(() -> doubleClickFirstRow(pendingTable));
        ShopSwingTestSupport.flushEdt();
        ShopSwingTestSupport.onEdt(() -> doubleClickFirstRow(processedTable));

        assertThat(opened).containsExactly("pending:true", "processed:false");
        verify(port).reviewApplication(new ReviewSellerApplicationCommand(
                "pending", SellerReviewDecision.APPROVE, null, 4));
        verify(port, times(1)).reviewApplication(any());
    }

    private static void doubleClickFirstRow(JTable table) {
        table.setRowSelectionInterval(0, 0);
        MouseEvent event = new MouseEvent(table, MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 4, 4, 2, false);
        for (java.awt.event.MouseListener listener : table.getMouseListeners()) {
            listener.mouseClicked(event);
        }
    }

    private static SellerApplicationView application(String id,
            SellerApplicationStatus status, long version) {
        return new SellerApplicationView(id, "student-1", "文具店", "简介", "文具",
                "contact", "经营计划", status, null, null, Instant.EPOCH,
                status == SellerApplicationStatus.PENDING ? null : Instant.EPOCH, version);
    }
}
