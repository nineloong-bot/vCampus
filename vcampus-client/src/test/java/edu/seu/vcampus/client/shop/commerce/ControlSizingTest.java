package edu.seu.vcampus.client.shop.commerce;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class ControlSizingTest {
    @Test void reportShopActionDoesNotStretchAcrossTheStorefront() throws Exception {
        CommercePanel[] panel = new CommercePanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new CommercePanel((c,b,k) -> c.equals("SHOP2_CATALOG_SHOP")
                    ? CompletableFuture.completedFuture(new edu.seu.vcampus.common.shop.catalog.CatalogDtos.Shop("s","店铺","","ACTIVE"))
                    : new CompletableFuture<>(), false);
            panel[0].setSize(1240,820);
            new CatalogPage(panel[0]).shop("s");
        });
        SwingUtilities.invokeAndWait(() -> {
            DemoFidelityTest.layout(panel[0]);
            JButton report=button(panel[0],"举报店铺");
            assertThat(report.getWidth()).isEqualTo(report.getPreferredSize().width);
            assertThat(report.getWidth()).isLessThan(180);
        });
    }
    @Test void searchTabsKeepTheirSizeWhenSwitching() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var ui = new CommercePanel((c,b,k) -> new CompletableFuture<>(), false);
            new CatalogPage(ui).search();
            Dimension goods = button(ui, "商品").getPreferredSize();
            Dimension shops = button(ui, "店铺").getPreferredSize();
            assertThat(goods).isEqualTo(shops);
            button(ui, "店铺").doClick();
            assertThat(button(ui, "商品").getPreferredSize()).isEqualTo(goods);
            assertThat(button(ui, "店铺").getPreferredSize()).isEqualTo(shops);
        });
    }
    @Test void quantityEditorHasOnlyOneBorderAndKeepsNumericBehavior() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(1,1,101,1));
            CommerceStyle.apply(spinner);
            assertThat(spinner.getBorder().getBorderInsets(spinner)).isEqualTo(new Insets(0,0,0,0));
            var editor = ((JSpinner.DefaultEditor)spinner.getEditor()).getTextField();
            assertThat(editor.getBorder().getBorderInsets(editor).left).isGreaterThan(0);
            spinner.setValue(spinner.getNextValue());
            assertThat(spinner.getValue()).isEqualTo(2);
        });
    }
    private static JButton button(Component c,String text) {
        if(c instanceof JButton b && text.equals(b.getText())) return b;
        if(c instanceof Container p) for(Component child:p.getComponents()) {
            JButton found=button(child,text);if(found!=null)return found;
        }
        return null;
    }
}
