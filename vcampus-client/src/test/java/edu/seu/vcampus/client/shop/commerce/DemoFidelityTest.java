package edu.seu.vcampus.client.shop.commerce;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;

class DemoFidelityTest {
    @Test void closeRestoresTheSameStorefrontAndKeepsModalFocusContained() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var ui=new CommercePanel((c,b,k)->new CompletableFuture<>(),false);
            JLabel original=new JLabel("当前商品列表");original.setName("original.list");
            ui.display("首页",original,null,null);new AccountPages(ui).open();
            assertThat(((Container)find(ui,"commerce.modal")).isFocusCycleRoot()).isTrue();
            ((JButton)find(ui,"commerce.modal.close")).doClick();
            assertThat(find(ui,"commerce.modal")).isNull();assertThat(find(ui,"original.list")).isSameAs(original);
        });
    }
    @Test void myUsesCenteredModalOverExistingStorefront() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var ui=new CommercePanel((c,b,k)->new CompletableFuture<>(),false);
            ui.setSize(1280,900);
            ui.display("首页",new JLabel("原商品列表"),null,null);
            new AccountPages(ui).open();
            layout(ui);
            Component modal=find(ui,"commerce.modal");
            assertThat(modal).as("My must retain storefront beneath a centered modal").isNotNull();
            assertThat(modal.getWidth()).isEqualTo(620);
            assertThat(find(ui,"commerce.modal.close")).isNotNull();
        });
    }
    @Test void verticalFormsDoNotStretchActionsToBottom() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JPanel form=CommerceTheme.form();
            JLabel title=new JLabel("余额");JButton first=new JButton("查看订单"),second=new JButton("申请开店");
            form.add(title);form.add(first);form.add(second);form.setSize(600,700);layout(form);
            assertThat(second.getY()+second.getHeight()).isLessThan(180);
            assertThat(second.getX()).isEqualTo(0);
        });
    }
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layout(nested);}
    static Component find(Component c,String name){if(name.equals(c.getName()))return c;if(c instanceof Container p)for(Component x:p.getComponents()){Component r=find(x,name);if(r!=null)return r;}return null;}
}
