package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class CartFidelityLayoutTest {
    @Test void styledBasketQuantityAndUpdateFitInsideNaturalRowHeight() throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            try {
                CommercePanel ui=new CommercePanel((command,body,key)->new CompletableFuture<>(),false);
                Sku sku=new Sku("sku","标准款",BigDecimal.TEN,10,0,true);
                Product product=new Product("p","s","校园生活店","校园笔记本","","","book","PUBLISHED","sku",false,0,List.of(sku));
                CartPage cart=new CartPage(ui);
                var method=CartPage.class.getDeclaredMethod("row",CartLine.class);method.setAccessible(true);
                JPanel row=(JPanel)method.invoke(cart,new CartLine("line",product,"sku",2));
                CommerceStyle.apply(row);row.setSize(850,row.getPreferredSize().height);layout(row);
                JPanel info=(JPanel)((BorderLayout)row.getLayout()).getLayoutComponent(BorderLayout.CENTER);
                Component quantityRow=info.getComponent(info.getComponentCount()-1);
                assertThat(quantityRow.getY()+quantityRow.getHeight()).isLessThanOrEqualTo(info.getHeight());
            } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }
    private static void layout(Container container) {
        container.doLayout();
        for(Component child:container.getComponents())if(child instanceof Container nested)layout(nested);
    }
}
