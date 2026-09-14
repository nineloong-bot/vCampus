package edu.seu.vcampus.client.shop.commerce;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
class CommerceUiTest {
 @Test void buyerAndAdminHaveDifferentNavigationAndFixedContent() throws Exception {
  SwingUtilities.invokeAndWait(() -> {
   var buyer = new CommercePanel((command, body, id) -> new CompletableFuture<>(), false);
   assertThat(buyer.getName()).isEqualTo("shop.commerce");
   assertThat(buyer.navigationText()).contains("购物车", "我的", "首页").doesNotContain("店铺管理");
   var admin = new CommercePanel((command, body, id) -> new CompletableFuture<>(), true);
   assertThat(admin.navigationText()).contains("店铺管理", "商品管理").doesNotContain("购物车");
   assertThat(CommerceTheme.ACCENT).isEqualTo(new java.awt.Color(0x306448));
  });
 }
 @Test void tableSupportsCtrlAndShiftWithoutSortingAwaySelection() throws Exception {
  SwingUtilities.invokeAndWait(() -> {
   JTable table = CommerceTheme.table(new String[]{"商品"},new Object[][]{{"A"},{"B"},{"C"}});
   table.setRowSelectionInterval(0,0); table.addRowSelectionInterval(2,2);
   assertThat(table.getSelectedRows()).containsExactly(0,2);
   table.setRowSelectionInterval(0,2); assertThat(table.getSelectedRows()).containsExactly(0,1,2);
  });
 }
}
