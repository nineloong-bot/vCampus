package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import org.junit.jupiter.api.Test;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class CommerceAsyncTest {
    @Test void returningToLoadingSourceResumesItsRequest() throws Exception {
        List<CompletableFuture<Serializable>> requests=new ArrayList<>();
        CommercePanel[] panel=new CommercePanel[1];JLabel label=new JLabel("加载中");
        SwingUtilities.invokeAndWait(()->{
            panel[0]=new CommercePanel((c,b,k)->{var future=new CompletableFuture<Serializable>();requests.add(future);return future;},false);
            panel[0].display("来源",label,null,null);
            panel[0].fetch("READ",EmptyRequest.INSTANCE,data->label.setText((String)data));
            Runnable source=panel[0].snapshot();
            panel[0].display("搜索",new JPanel(),null,null);
            source.run();assertThat(requests).hasSize(2);
            requests.get(0).complete("旧响应");requests.get(1).complete("恢复完成");
        });
        SwingUtilities.invokeAndWait(()->assertThat(label.getText()).isEqualTo("恢复完成"));
    }

    @Test void mappedPaymentGuidanceSurvivesGenericServerMessage() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new CommercePanel((c,b,k)->new CompletableFuture<>(),false);
            panel.failure(new CommerceFailure("WALLET_INSUFFICIENT_BALANCE","订单操作未完成，请刷新状态后重试"));
            assertThat(text(panel)).contains("余额不足，请先充值后重试");
            panel.failure(new CommerceFailure("SHOP_CATALOG_INVALID_REQUEST","价格不能小于0"));
            assertThat(text(panel)).contains("价格不能小于0");
        });
    }
    private static String text(Component component) {
        StringBuilder result=new StringBuilder();
        if(component instanceof JLabel label)result.append(label.getText());
        if(component instanceof Container container)for(Component child:container.getComponents())result.append(text(child));
        return result.toString();
    }
}
