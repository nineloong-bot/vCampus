package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.library.service.LibraryRequestException;
import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.wallet.WalletOperationResult;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LibraryFinePanelTest {
    private final LibraryClientService service = mock(LibraryClientService.class);

    private LibraryFineView fine(String id) {
        var now = Instant.parse("2026-09-01T00:00:00Z");
        return new LibraryFineView(new LoanView(id, "copy", "book", "user", now, now, now, 0,
                LoanStatus.RETURNED, 1, "STUDENT", "Java", "BC-1", new BigDecimal("0.50"),
                BigDecimal.TEN, ReturnCondition.MINOR_DAMAGE), null);
    }

    @Test void cancelDoesNotPayAndPendingPaymentCannotBeSubmittedTwice() throws Exception {
        when(service.getMyFines(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(fine("loan-1")), 1, 20, 1)));
        var payment = new CompletableFuture<WalletOperationResult>();
        when(service.payFine("loan-1")).thenReturn(payment);
        var panel = new LibraryFinePanel(service, false);
        SwingUtilities.invokeAndWait(panel::refresh); flush();
        SwingUtilities.invokeAndWait(() -> panel.table.setRowSelectionInterval(0, 0));
        confirm(panel, JOptionPane.CANCEL_OPTION);
        verify(service, never()).payFine(anyString());
        confirm(panel, JOptionPane.OK_OPTION);
        SwingUtilities.invokeAndWait(() -> button(panel, "缴纳所选罚款").doClick());
        verify(service, times(1)).payFine("loan-1");
        assertThat(button(panel, "刷新罚款").isEnabled()).isFalse();
        payment.complete(new WalletOperationResult("receipt", 8950)); flush();
        assertThat(panel.table.getValueAt(0, 6)).isEqualTo("已缴纳");
        assertThat(panel.table.getValueAt(0, 7)).isEqualTo("receipt");
        assertThat(button(panel, "缴纳所选罚款").isEnabled()).isFalse();
        assertThat(panel.status.getText()).contains("89.50");
    }

    @Test void failedPaymentStaysDueAndCanRetryWhilePaginationLoadsTheSelectedLoan() throws Exception {
        when(service.getMyFines(any())).thenAnswer(call -> {
            LibraryFineQuery query = call.getArgument(0);
            return CompletableFuture.completedFuture(new PageResult<>(List.of(fine("loan-" + query.page())),
                    query.page(), 20, 21));
        });
        when(service.payFine("loan-2")).thenReturn(CompletableFuture.failedFuture(
                new LibraryRequestException("WALLET_INSUFFICIENT_BALANCE", "钱包余额不足，请充值")));
        var panel = new LibraryFinePanel(service, false);
        SwingUtilities.invokeAndWait(panel::refresh); flush();
        SwingUtilities.invokeAndWait(() -> button(panel, "下一页").doClick()); flush();
        SwingUtilities.invokeAndWait(() -> panel.table.setRowSelectionInterval(0, 0));
        confirm(panel, JOptionPane.OK_OPTION); flush();
        assertThat(panel.table.getValueAt(0, 6)).isEqualTo("待缴纳");
        assertThat(button(panel, "缴纳所选罚款").isEnabled()).isTrue();
        assertThat(panel.status.getText()).contains("余额不足");
        verify(service).getMyFines(new LibraryFineQuery(2, 20));
        verify(service).payFine("loan-2");
    }

    @Test void administratorCanQueryButHasNoDebitControl() throws Exception {
        when(service.getAllFines(any())).thenReturn(CompletableFuture.completedFuture(
                new PageResult<>(List.of(fine("loan")), 1, 20, 1)));
        var panel = new LibraryFinePanel(service, true);
        SwingUtilities.invokeAndWait(panel::refresh); flush();
        assertThat(button(panel, "缴纳所选罚款")).isNull();
        verify(service).getAllFines(new LibraryFineQuery(1, 20));
        verify(service, never()).getMyFines(any());
    }

    private static void confirm(LibraryFinePanel panel, int result) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var dialogs = mockStatic(JOptionPane.class)) {
                dialogs.when(() -> JOptionPane.showConfirmDialog(eq(panel), contains("10.50"), eq("确认缴纳罚款"),
                        eq(JOptionPane.OK_CANCEL_OPTION), eq(JOptionPane.QUESTION_MESSAGE))).thenReturn(result);
                button(panel, "缴纳所选罚款").doClick();
            }
        });
    }

    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

    private static JButton button(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton b && text.equals(b.getText())) return b;
            if (child instanceof Container c) {
                var found = button(c, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
