"""商城订单、库存流水和虚拟钱包分录。"""
from datetime import timedelta
from decimal import Decimal


def build_orders(record, now, available, products, skus, shops):
    specs = (
        ("PENDING_PAYMENT", "PENDING_PAYMENT", "PENDING", "ACTIVE"),
        ("PENDING_PAYMENT", "PENDING_PAYMENT", "PENDING", "ACTIVE"),
        ("PENDING_PAYMENT", "PENDING_PAYMENT", "PENDING", "ACTIVE"),
        ("PENDING_PAYMENT", "PENDING_PAYMENT", "PENDING", "ACTIVE"),
        ("PAID", "PAID", "SUCCEEDED", "CONSUMED"),
        ("PAID", "PAID", "SUCCEEDED", "CONSUMED"),
        ("PAID", "PAID", "SUCCEEDED", "CONSUMED"),
        ("SHIPPED", "SHIPPED", "SUCCEEDED", "CONSUMED"),
        ("SHIPPED", "SHIPPED", "SUCCEEDED", "CONSUMED"),
        ("SHIPPED", "SHIPPED", "SUCCEEDED", "CONSUMED"),
        ("COMPLETED", "COMPLETED", "SUCCEEDED", "CONSUMED"),
        ("COMPLETED", "COMPLETED", "SUCCEEDED", "CONSUMED"),
        ("COMPLETED", "COMPLETED", "SUCCEEDED", "CONSUMED"),
        ("COMPLETED", "COMPLETED", "SUCCEEDED", "CONSUMED"),
        ("CANCELLED", "CANCELLED", "CANCELLED", "RELEASED"),
        ("CANCELLED", "CANCELLED", "EXPIRED", "RELEASED"),
        ("PREPARING", "REFUND_PENDING", "SUCCEEDED", "CONSUMED"),
        ("CANCELLED", "REFUNDED", "SUCCEEDED", "CONSUMED"),
    )
    buyers = ["bulk-student-user-0101", "bulk-student-user-0102", "bulk-student-user-0103",
              "bulk-student-user-0104", "bulk-student-user-0105"]
    balances = {buyers[0]: 50000, buyers[1]: 500, buyers[2]: 0,
                buyers[3]: 20000, buyers[4]: 10000}
    for number, (legacy, lifecycle, payment_status, reservation_status) in enumerate(specs, 1):
        shop, product, sku = available[(number * 5) % len(available)]
        buyer = buyers[(number - 1) % len(buyers)]
        if payment_status == "SUCCEEDED":
            paid_buyers = (buyers[0], buyers[3], buyers[0])
            buyer = paid_buyers[(number - 5) % len(paid_buyers)]
        quantity = 2 if number == 1 else 1
        amount = sku["unitPrice"] * quantity
        created = now - timedelta(days=number + 1)
        group = f"bulk-order-group-{number:03d}"
        order = f"bulk-order-demo-{number:03d}"
        payment = f"bulk-payment-demo-{number:03d}"
        item_ids = [f"bulk-order-item-{number:03d}-1"]
        if number == 1:
            second = available[(number * 5 + 1) % len(available)]
            amount += second[2]["unitPrice"]
            item_ids.append(f"bulk-order-item-{number:03d}-2")
        group_status = "PENDING_PAYMENT" if legacy == "PENDING_PAYMENT" else "COMPLETED" if lifecycle == "COMPLETED" else "CANCELLED" if lifecycle in ("CANCELLED", "REFUNDED") else "PAID"
        record("tblOrderGroup", orderGroupId=group, buyerUserId=buyer, totalAmount=amount,
               groupStatus=group_status, createdAt=created, rowVersion=int(payment_status != "PENDING"))
        record("tblOrder", orderId=order, orderGroupId=group, shopId=shop["shopId"],
               orderNumber=f"VC202609{number:04d}", orderAmount=amount, orderStatus=legacy,
               createdAt=created, paidAt=created + timedelta(minutes=5) if payment_status == "SUCCEEDED" else None,
               shippedAt=created + timedelta(days=1) if legacy in ("SHIPPED", "COMPLETED") else None,
               completedAt=created + timedelta(days=2) if legacy == "COMPLETED" else None,
               rowVersion=int(payment_status != "PENDING"))
        record("tblShopOrderState", orderId=order, lifecycle=lifecycle,
               expiresAt=now + timedelta(days=3) if lifecycle == "PENDING_PAYMENT" else created + timedelta(days=7),
               refundReason="误选规格，希望取消本次购买" if lifecycle == "REFUND_PENDING" else
               "商品已退款，库存已恢复" if lifecycle == "REFUNDED" else None)
        _item(record, item_ids[0], order, shop, product, sku, quantity, lifecycle, created)
        if number == 1:
            second = available[(number * 5 + 1) % len(available)]
            _item(record, item_ids[1], order, shop, second[1], second[2], 1, lifecycle, created)
        record("tblPayment", paymentId=payment, orderGroupId=group,
               paymentNumber=f"PAY202609{number:04d}",
               successfulChannel="WECHAT" if payment_status == "SUCCEEDED" else None,
               amount=amount, paymentStatus=payment_status,
               completedAt=created + timedelta(minutes=5) if payment_status != "PENDING" else None,
               rowVersion=int(payment_status != "PENDING"))
        if payment_status != "EXPIRED":
            record("tblPaymentAttempt", attemptId=f"bulk-payment-attempt-{number:03d}",
                   paymentId=payment, channel="WECHAT",
                   attemptStatus="FAILED" if payment_status == "PENDING" else payment_status,
                   createdAt=created + timedelta(minutes=2), completedAt=created + timedelta(minutes=2))
        for item_index, item_id in enumerate(item_ids, 1):
            item_sku = sku if item_index == 1 else second[2]
            record("tblInventoryReservation", reservationId=f"bulk-reservation-demo-{number:03d}-{item_index}",
                   paymentId=payment, skuId=item_sku["skuId"], quantity=quantity if item_index == 1 else 1,
                   reservationStatus=reservation_status, expiresAt=created + timedelta(days=3),
                   releasedAt=None if reservation_status == "ACTIVE" else created + timedelta(minutes=5))
            movement = "RESERVE" if reservation_status == "ACTIVE" else "PAY" if reservation_status == "CONSUMED" else "RELEASE"
            record("tblShopInventoryMovement", movementId=f"bulk-movement-{number:03d}-{item_index}-1",
                   orderItemId=item_id, skuId=item_sku["skuId"], movementKind=movement,
                   quantity=quantity if item_index == 1 else 1, createdAt=created)
            if lifecycle == "REFUNDED":
                record("tblShopInventoryMovement", movementId=f"bulk-movement-{number:03d}-{item_index}-2",
                       orderItemId=item_id, skuId=item_sku["skuId"], movementKind="REFUND",
                       quantity=quantity if item_index == 1 else 1, createdAt=created + timedelta(days=1))
        cents = int(amount * 100)
        if payment_status == "SUCCEEDED":
            _wallet_operation(record, number, "PAYMENT", buyer, shop["ownerUserId"], group,
                              amount, balances[buyer] - cents, created)
        if lifecycle == "REFUNDED":
            _wallet_operation(record, number, "REFUND", buyer, shop["ownerUserId"], group,
                              amount, balances[buyer], created + timedelta(days=1))
        elif payment_status == "SUCCEEDED":
            if lifecycle == "COMPLETED":
                _wallet_operation(record, number, "INCOME", shop["ownerUserId"], buyer, group,
                                  amount, cents, created + timedelta(days=2))
        if payment_status == "SUCCEEDED":
            escrow_status = "REFUNDED" if lifecycle == "REFUNDED" else "SETTLED" if lifecycle == "COMPLETED" else "HELD"
            record("tblWalletEscrow", orderKey=group, buyerId=buyer, sellerId=shop["ownerUserId"],
                   amountCents=int(amount * 100), escrowStatus=escrow_status)
        if lifecycle == "REFUND_PENDING":
            action = "REFUND_REQUEST"
        elif lifecycle == "REFUNDED":
            action = "REFUND_APPROVE"
        elif legacy == "CANCELLED":
            action = "CANCEL"
        else:
            action = "CREATE"
        record("tblShopOrderEvent", eventId=f"bulk-order-event-{number:03d}", orderId=order,
               actionName=action, actorUserId=buyer, reason="演示订单状态记录",
               previousState="NEW", nextState=lifecycle, createdAt=created)


def _item(record, item_id, order, shop, product, sku, quantity, lifecycle, created):
    record("tblOrderItem", orderItemId=item_id, orderId=order, skuId=sku["skuId"],
           productNameSnapshot=product["productName"], skuNameSnapshot=sku["skuName"],
           shopNameSnapshot=shop["shopName"], unitPrice=sku["unitPrice"], quantity=quantity,
           lineAmount=sku["unitPrice"] * quantity)
    line_state = "RESERVED" if lifecycle == "PENDING_PAYMENT" else "REFUNDED" if lifecycle == "REFUNDED" else "RELEASED" if lifecycle == "CANCELLED" else "CONSUMED"
    record("tblShopOrderLineState", orderItemId=item_id, productId=product["productId"], lineState=line_state)


def _wallet_operation(record, number, kind, actor, peer, order, amount, balance_after, created):
    operation = f"bulk-wallet-{kind.lower()}-{number:03d}"
    cents = int(amount * 100)
    record("tblWalletOperation", operationId=operation, businessKey=f"I:DEMO:{kind}:{order}",
           operationType=kind, actorId=actor, peerId=peer, orderKey=order,
           amountCents=cents, balanceAfter=balance_after, createdAt=created)
    record("tblWalletEntry", entryId=f"{operation}-from", operationId=operation,
           accountKind="USER" if kind != "INCOME" else "ESCROW", accountKey=actor if kind != "INCOME" else order,
           deltaCents=-cents)
    record("tblWalletEntry", entryId=f"{operation}-to", operationId=operation,
           accountKind="ESCROW" if kind == "PAYMENT" else "USER", accountKey=order if kind == "PAYMENT" else actor,
           deltaCents=cents)
