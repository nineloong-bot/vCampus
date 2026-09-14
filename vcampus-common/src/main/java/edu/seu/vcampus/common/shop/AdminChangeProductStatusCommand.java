package edu.seu.vcampus.common.shop;

import java.io.Serializable;

/**
 * 商城管理员修改商品状态的请求命令。
 *  *
 *  * @param productId 商品标识
 *  * @param status 目标商品状态
 *  * @param reason 变更原因
 */
public record AdminChangeProductStatusCommand(String shopId,
        ChangeProductStatusCommand command) implements Serializable { }
