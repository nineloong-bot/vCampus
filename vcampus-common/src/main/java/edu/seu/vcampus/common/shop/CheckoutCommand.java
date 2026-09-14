package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * 购物车结算与下单请求命令。
 *  *
 *  * @param items 选购结算条目列表
 *  * @param contactName 收货人姓名
 *  * @param contactPhone 联系电话
 *  * @param shippingAddress 收货地址
 */
public record CheckoutCommand(List<CheckoutItem> items, boolean acceptLatestPrice)
        implements Serializable {
    public CheckoutCommand {
        items = List.copyOf(items);
    }
}
