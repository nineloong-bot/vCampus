package edu.seu.vcampus.common.shop;

import java.io.Serializable;
import java.util.List;

/**
 * 已支付订单的历史记录与分页查询视图。
 *  *
 *  * @param orders 订单列表
 *  * @param totalCount 总订单数
 */
public record PaidOrderHistory(List<PaidOrderView> orders) implements Serializable {
    public PaidOrderHistory {
        orders = List.copyOf(orders);
    }
}
