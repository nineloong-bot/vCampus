"""订单金额、支付状态和库存变化来自同一份商品引用。"""
from datetime import timedelta


def build_orders(record, now, available):
    statuses = ("PENDING_PAYMENT", "PAID", "PREPARING", "SHIPPED", "COMPLETED", "CANCELLED")
    channels = ("WECHAT", "ALIPAY", "BANK_CARD")
    for n in range(1, 601):
        status = statuses[(n - 1) // 100]
        paid = status in ("PAID", "PREPARING", "SHIPPED", "COMPLETED")
        pending = status == "PENDING_PAYMENT"
        shop, product, sku = available[(n - 1) % len(available)]
        quantity = n % 3 + 1
        amount = sku["unitPrice"] * quantity
        created = now - timedelta(days=10, minutes=n)
        resolved = created + timedelta(minutes=2)
        group_id, order_id, payment_id = (f"bulk-{kind}-{n:04d}" for kind in ("group", "order", "payment"))
        group_status = "PAID" if status in ("PREPARING", "SHIPPED") else status
        record("tblOrderGroup", orderGroupId=group_id, buyerUserId=f"bulk-student-user-{n + 100:04d}",
               totalAmount=amount, groupStatus=group_status, createdAt=created, rowVersion=int(not pending))
        record("tblOrder", orderId=order_id, orderGroupId=group_id, shopId=shop["shopId"],
               orderNumber=f"bulk-ordno-{n:06d}", orderAmount=amount, orderStatus=status,
               createdAt=created, paidAt=resolved if paid else None,
               shippedAt=created + timedelta(days=1) if status in ("SHIPPED", "COMPLETED") else None,
               completedAt=created + timedelta(days=2) if status == "COMPLETED" else None,
               rowVersion=int(not pending))
        record("tblOrderItem", orderItemId=f"bulk-orderitem-{n:04d}", orderId=order_id,
               skuId=sku["skuId"], productNameSnapshot=product["productName"],
               skuNameSnapshot=sku["skuName"], shopNameSnapshot=shop["shopName"],
               unitPrice=sku["unitPrice"], quantity=quantity, lineAmount=amount)
        channel = channels[(n - 1) % 3]
        # 一半取消订单来自超时；其余来自用户取消。
        expired = status == "CANCELLED" and n % 2 == 0
        expiry = now + timedelta(days=7) if pending else created + timedelta(minutes=15)
        if expired:
            resolved = expiry + timedelta(seconds=1)
        payment_status = "PENDING" if pending else "SUCCEEDED" if paid else "EXPIRED" if expired else "CANCELLED"
        record("tblPayment", paymentId=payment_id, orderGroupId=group_id,
               paymentNumber=f"bulk-payno-{n:06d}", successfulChannel=channel if paid else None,
               amount=amount, paymentStatus=payment_status, completedAt=None if pending else resolved,
               rowVersion=int(not pending))
        # 超时不虚构支付尝试；待支付样本保留一次失败尝试供重试。
        if not expired:
            record("tblPaymentAttempt", attemptId=f"bulk-attempt-{n:04d}", paymentId=payment_id,
                   channel=channel, attemptStatus="FAILED" if pending else "SUCCEEDED" if paid else "CANCELLED",
                   createdAt=resolved, completedAt=resolved)
        reservation_status = "ACTIVE" if pending else "CONSUMED" if paid else "RELEASED"
        record("tblInventoryReservation", reservationId=f"bulk-reservation-{n:04d}",
               paymentId=payment_id, skuId=sku["skuId"], quantity=quantity,
               reservationStatus=reservation_status, expiresAt=expiry,
               releasedAt=None if pending else resolved)
        if pending:
            sku["reservedQuantity"] += quantity
        elif paid:
            sku["stockQuantity"] -= quantity
            product["salesCount"] += quantity
            product["rowVersion"] += 1
        if pending or paid:
            sku["rowVersion"] += 1
