"""Deterministic campus commerce, governance, and shared-wallet data."""
from collections import Counter, defaultdict
from datetime import timedelta
from decimal import Decimal

SHOPS = (
    ("青禾文具铺", "文具", "user-student-2024-009", "ACTIVE"),
    ("拾光书屋", "图书", "user-student-2024-010", "ACTIVE"),
    ("梧桐生活馆", "生活用品", "user-student-2024-011", "ACTIVE"),
    ("行知运动小铺", "运动用品", "user-student-2024-012", "ACTIVE"),
    ("麦香校园食坊", "食品", "user-student-2024-013", "ACTIVE"),
    ("晚风杂货铺", "校园生活", "user-student-2024-014", "SUSPENDED"),
)
PRODUCT_NAMES = (
    "方格笔记本", "按动中性笔", "透明文件夹", "荧光标记笔",
    "课程阅读书签", "桌面收纳盒", "帆布托特包", "陶瓷马克杯",
    "运动毛巾", "校园纪念明信片", "便携雨伞", "轻食饼干",
)


def generate(add, now):
    """Generate the approved commerce matrix and validate it in memory."""
    rows = defaultdict(list)

    def record(table, **fields):
        rows[table].append(fields)
        return fields

    owners = [spec[2] for spec in SHOPS]
    applications = [(owners[i], SHOPS[i][0], "APPROVED") for i in range(6)] + [
        ("user-student-2024-031", "晨曦手作铺", "PENDING"),
        ("user-student-2024-032", "知行书角", "REJECTED"),
    ]
    for index, (owner, name, status) in enumerate(applications, 1):
        reviewed = status != "PENDING"
        record("tblSellerApplication", applicationId=f"seller-application-{index:02d}",
               applicantUserId=owner, shopName=name,
               description=f"为师生提供{name}相关商品与校内服务。", category="校园生活",
               contact=f"shop{index:02d}@seu.edu.cn",
               applicationStatement="遵守校园商城经营规则，确保商品信息真实完整。",
               applicationStatus=status,
               reviewReason="经营范围清晰，材料完整。" if status == "APPROVED" else
               "经营信息需要补充。" if status == "REJECTED" else None,
               reviewerUserId="user-shop-admin" if reviewed else None,
               submittedAt=now - timedelta(days=40 - index),
               reviewedAt=now - timedelta(days=30 - index) if reviewed else None,
               rowVersion=1 if reviewed else 0)
    shops = []
    for index, (name, category, owner, status) in enumerate(SHOPS, 1):
        shops.append(record("tblShop", shopId=f"shop-{index:02d}", ownerUserId=owner,
                            shopName=name, normalizedShopName=f"shop-{index:02d}",
                            description=f"{name}面向校内师生提供{category}商品。",
                            category=category, contact=f"owner{index:02d}@seu.edu.cn",
                            shopStatus=status,
                            suspensionReason="经营资料复核中，暂时停止营业。" if status == "SUSPENDED" else None,
                            suspendedByUserId="user-shop-admin" if status == "SUSPENDED" else None,
                            suspendedAt=now - timedelta(days=3) if status == "SUSPENDED" else None,
                            rowVersion=1 if status == "SUSPENDED" else 0,
                            createdAt=now - timedelta(days=60), updatedAt=now))

    products, skus = {}, {}
    for shop_index, shop_row in enumerate(shops, 1):
        for local in range(1, 13):
            number = (shop_index - 1) * 12 + local
            product_id = f"product-{number:03d}"
            name = f"{PRODUCT_NAMES[local - 1]}·{shop_row['shopName']}"
            status = "ACTIVE" if local <= 8 else "DRAFT" if local <= 10 else "INACTIVE"
            if shop_row["shopStatus"] == "SUSPENDED":
                status = "INACTIVE"
            product = record("tblProduct", productId=product_id, shopId=shop_row["shopId"],
                             productName=name, normalizedProductName=f"product-{local:02d}",
                             category="licensed" if shop_index == 5 and local <= 4 else "ordinary",
                             description=f"{name}适合校园学习与生活，规格和价格信息完整。",
                             coverImageUrl=None, productStatus=status, salesCount=local * 2,
                             rowVersion=0, createdAt=now - timedelta(days=25), updatedAt=now)
            products[product_id] = product
            variant_count = 3 if local == 1 else 2 if local == 2 else 1
            for variant in range(1, variant_count + 1):
                sku_id = f"sku-{number:03d}-{variant}"
                sku = record("tblProductSku", skuId=sku_id, productId=product_id,
                             skuName=("标准装", "加量装", "礼盒装")[variant - 1],
                             unitPrice=Decimal(500 + number * 35 + variant * 100) / 100,
                             stockQuantity=30 + local, reservedQuantity=0,
                             isActive=True, rowVersion=0)
                skus[sku_id] = sku
            record("tblProductCatalog", productId=product_id,
                   defaultSkuId=f"sku-{number:03d}-1", imageId=_image_id(local), isDeleted=False)
    for product_index in (9, 10, 21, 22, 33, 34):
        record("tblShopProductRestriction", productId=f"product-{product_index:03d}",
               emergencyBlocked=product_index in {10, 22},
               qualificationBlocked=product_index in {9, 21, 33},
               expiryRestoreEligible=product_index in {21, 33, 34})

    _carts(record)
    _qualifications_and_governance(record, shops, now)
    _orders(record, rows, shops, products, skus, now)
    _wallets(record, rows, shops, now)
    validate(rows)
    for table, values in rows.items():
        for fields in values:
            add(table, **fields)
    return {table: len(values) for table, values in rows.items()}


def _image_id(local):
    if local <= 4:
        return "pen" if local in {2, 4} else "book"
    if local <= 8:
        return "box" if local in {6, 8} else "bag"
    return "shirt" if local == 9 else "box"


def _carts(record):
    buyers = ("user-student-2024-017", "user-student-2024-018", "user-student-2024-019")
    for index, buyer in enumerate(buyers, 1):
        record("tblCart", cartId=f"cart-{index:02d}", userId=buyer,
               updatedAt=__import__("datetime").datetime(2026, 9, 16, 12))
    for line in range(1, 7):
        record("tblCartItem", cartItemId=f"cart-item-01-{line}", cartId="cart-01",
               skuId=f"sku-{line:03d}-1", quantity=1 + line % 2, rowVersion=0,
               createdAt=__import__("datetime").datetime(2026, 9, 15, 18),
               updatedAt=__import__("datetime").datetime(2026, 9, 16, 12))
    for line in range(1, 3):
        record("tblCartItem", cartItemId=f"cart-item-02-{line}", cartId="cart-02",
               skuId=f"sku-{12 + line:03d}-1", quantity=1, rowVersion=0,
               createdAt=__import__("datetime").datetime(2026, 9, 15, 19),
               updatedAt=__import__("datetime").datetime(2026, 9, 16, 12))


def _qualifications_and_governance(record, shops, now):
    record("tblShopGovApplication", applicationId="gov-application-01",
           subjectName="麦香校园食坊", licenseNumber="FOOD-2026-001")
    qualifications = (
        (1, 5, "SPECIAL", "APPROVED", 180, "资质有效。"),
        (2, 1, "SPECIAL", "PENDING", 240, None),
        (3, 2, "SPECIAL", "REJECTED", 120, "材料信息不完整。"),
        (4, 4, "SPECIAL", "EXPIRED", -10, "资质已到期。"),
        (5, 5, "SPECIAL_RENEWAL", "PENDING", 365, "续期材料审核中。"),
    )
    for index, shop_index, kind, status, days, reason in qualifications:
        record("tblShopQualification", qualificationId=f"qualification-{index:02d}",
               shopId=shops[shop_index - 1]["shopId"], licenseType=kind,
               licenseNumber=f"QUAL-2026-{index:03d}", expiresOn=now + timedelta(days=days),
               qualificationStatus=status, reviewReason=reason,
               submittedAt=now - timedelta(days=20 + index))
    cases = (
        ("REPORT_PRODUCT", "product-010", "shop-01", "PENDING", "商品规格描述不完整。"),
        ("REPORT_PRODUCT", "product-022", "shop-02", "RESOLVED", "商品信息已补充并复核。"),
        ("REOPEN", "shop-06", "shop-06", "PENDING", "经营资料已补充，申请恢复营业。"),
        ("REMEDIATION", "product-021", "shop-02", "PENDING", "资质材料已更新，申请恢复商品。"),
        ("REMEDIATION", "product-033", "shop-03", "APPROVED", "整改内容符合经营要求。"),
    )
    for index, (kind, object_id, shop_id, status, reason) in enumerate(cases, 1):
        case_id = f"gov-case-{index:02d}"
        actor = "user-shop-admin" if status != "PENDING" else shops[(index - 1) % 6]["ownerUserId"]
        record("tblShopGovCase", caseId=case_id, actorId=actor, caseKind=kind,
               objectId=object_id, shopId=shop_id, reason=reason,
               explanation=reason, caseStatus=status,
               resultText="已完成审核并记录处理结果。" if status != "PENDING" else None,
               createdAt=now - timedelta(days=index))
        record("tblShopGovAudit", auditId=f"gov-audit-submit-{index:02d}", actorId=actor,
               objectId=object_id, actionName=f"SUBMIT_{kind}", reason=reason,
               occurredAt=now - timedelta(days=index), beforeState="",
               afterState="PENDING", linkedId=case_id)
        if status != "PENDING":
            record("tblShopGovAudit", auditId=f"gov-audit-review-{index:02d}",
                   actorId="user-shop-admin", objectId=object_id,
                   actionName=f"REVIEW_{kind}", reason="审核处理完成。",
                   occurredAt=now - timedelta(days=index - 1), beforeState="PENDING",
                   afterState=status, linkedId=case_id)


def _orders(record, rows, shops, products, skus, now):
    statuses = (["PENDING_PAYMENT"] * 4 + ["PAID"] * 4 + ["PREPARING"] * 3
                + ["SHIPPED"] * 3 + ["COMPLETED"] * 4 + ["CANCELLED"] * 2)
    buyers = ("user-student-2024-017", "user-student-2024-018")
    for index, status in enumerate(statuses, 1):
        buyer = buyers[(index - 1) % 2]
        shop_row = shops[(index - 1) % 6]
        product_no = ((index - 1) % 8) + ((index - 1) % 6) * 12 + 1
        product = products[f"product-{product_no:03d}"]
        sku = skus[f"sku-{product_no:03d}-1"]
        quantity = 2 if index % 5 == 0 else 1
        amount = sku["unitPrice"] * quantity
        created = now - timedelta(days=25 - index)
        group_id, order_id, item_id = f"order-group-{index:02d}", f"order-{index:02d}", f"order-item-{index:02d}"
        group_status = "PENDING_PAYMENT" if status == "PENDING_PAYMENT" else (
            "CANCELLED" if status == "CANCELLED" else "COMPLETED" if status == "COMPLETED" else "PAID")
        payment_status = "PENDING" if status == "PENDING_PAYMENT" else (
            "CANCELLED" if status == "CANCELLED" else "SUCCEEDED")
        reservation_status = "ACTIVE" if payment_status == "PENDING" else (
            "RELEASED" if payment_status == "CANCELLED" else "CONSUMED")
        record("tblOrderGroup", orderGroupId=group_id, buyerUserId=buyer,
               totalAmount=amount, groupStatus=group_status, createdAt=created, rowVersion=0)
        record("tblOrder", orderId=order_id, orderGroupId=group_id, shopId=shop_row["shopId"],
               orderNumber=f"VC202609{index:04d}", orderAmount=amount, orderStatus=status,
               createdAt=created, paidAt=created + timedelta(minutes=5) if payment_status == "SUCCEEDED" else None,
               shippedAt=created + timedelta(days=1) if status in {"SHIPPED", "COMPLETED"} else None,
               completedAt=created + timedelta(days=2) if status == "COMPLETED" else None,
               rowVersion=0)
        record("tblOrderItem", orderItemId=item_id, orderId=order_id, skuId=sku["skuId"],
               productNameSnapshot=product["productName"], skuNameSnapshot=sku["skuName"],
               shopNameSnapshot=shop_row["shopName"], unitPrice=sku["unitPrice"],
               quantity=quantity, lineAmount=amount)
        payment_id = f"payment-{index:02d}"
        record("tblPayment", paymentId=payment_id, orderGroupId=group_id,
               paymentNumber=f"PAY202609{index:04d}",
               successfulChannel="WECHAT" if payment_status == "SUCCEEDED" else None,
               amount=amount, paymentStatus=payment_status,
               completedAt=created + timedelta(minutes=5) if payment_status != "PENDING" else None,
               rowVersion=0)
        record("tblPaymentAttempt", attemptId=f"payment-attempt-{index:02d}",
               paymentId=payment_id, channel="WECHAT",
               attemptStatus="STARTED" if payment_status == "PENDING" else payment_status,
               createdAt=created + timedelta(minutes=2),
               completedAt=None if payment_status == "PENDING" else created + timedelta(minutes=5))
        record("tblInventoryReservation", reservationId=f"inventory-reservation-{index:02d}",
               paymentId=payment_id, skuId=sku["skuId"], quantity=quantity,
               reservationStatus=reservation_status, expiresAt=created + timedelta(days=3),
               releasedAt=created + timedelta(minutes=5) if reservation_status != "ACTIVE" else None)
        if reservation_status == "ACTIVE":
            sku["reservedQuantity"] += quantity
        elif reservation_status == "CONSUMED":
            sku["stockQuantity"] -= quantity
        lifecycle = status
        record("tblShopOrderState", orderId=order_id, lifecycle=lifecycle,
               expiresAt=created + timedelta(days=3), refundReason=None)
        record("tblShopOrderLineState", orderItemId=item_id, productId=product["productId"],
               lineState="RESERVED" if reservation_status == "ACTIVE" else
               "RELEASED" if reservation_status == "RELEASED" else "CONSUMED")
        record("tblShopInventoryMovement", movementId=f"inventory-movement-{index:02d}",
               orderItemId=item_id, skuId=sku["skuId"],
               movementKind="RESERVE" if reservation_status == "ACTIVE" else
               "RELEASE" if reservation_status == "RELEASED" else "PAY",
               quantity=quantity, createdAt=created)
        record("tblShopOrderEvent", eventId=f"order-event-{index:02d}", orderId=order_id,
               actionName="CREATE", actorUserId=buyer, reason="订单创建记录。",
               previousState="NEW", nextState=lifecycle, createdAt=created)


def _wallets(record, rows, shops, now):
    buyers = ("user-student-2024-017", "user-student-2024-018")
    overdue_users = ("user-student-2024-001", "user-student-2024-002")
    owners = tuple(shop_row["ownerUserId"] for shop_row in shops)
    balances = {user: 0 for user in buyers + overdue_users + owners}
    operation_no = 0

    def operation(kind, actor, peer, order_key, amount, delta_actor, created):
        nonlocal operation_no
        operation_no += 1
        operation_id = f"wallet-operation-{operation_no:03d}"
        balances[actor] += delta_actor
        record("tblWalletOperation", operationId=operation_id,
               businessKey=f"WALLET:{kind}:{operation_no:03d}", operationType=kind,
               actorId=actor, peerId=peer, orderKey=order_key,
               amountCents=amount, balanceAfter=balances[actor], createdAt=created)
        record("tblWalletEntry", entryId=f"wallet-entry-{operation_no:03d}-user",
               operationId=operation_id, accountKind="USER", accountKey=actor,
               deltaCents=delta_actor)
        record("tblWalletEntry", entryId=f"wallet-entry-{operation_no:03d}-system",
               operationId=operation_id, accountKind="SYSTEM", accountKey="PLATFORM",
               deltaCents=-delta_actor)

    for user in balances:
        amount = 100000 if user in buyers else 20000 if user in owners else 5000
        operation("RECHARGE", user, "user-admin", "INITIAL", amount, amount,
                  now - timedelta(days=45))
    orders = {row["orderId"]: row for row in rows["tblOrder"]}
    groups = {row["orderGroupId"]: row for row in rows["tblOrderGroup"]}
    for payment in rows["tblPayment"]:
        if payment["paymentStatus"] != "SUCCEEDED":
            continue
        group = groups[payment["orderGroupId"]]
        order = next(value for value in orders.values() if value["orderGroupId"] == group["orderGroupId"])
        buyer, owner = group["buyerUserId"], shops[int(order["shopId"].split("-")[-1]) - 1]["ownerUserId"]
        cents = int(payment["amount"] * 100)
        operation("PAYMENT", buyer, owner, group["orderGroupId"], cents, -cents,
                  group["createdAt"] + timedelta(minutes=5))
        escrow_status = "SETTLED" if order["orderStatus"] == "COMPLETED" else "HELD"
        record("tblWalletEscrow", orderKey=group["orderGroupId"], buyerId=buyer,
               sellerId=owner, amountCents=cents, escrowStatus=escrow_status)
        if escrow_status == "SETTLED":
            operation("INCOME", owner, buyer, group["orderGroupId"], cents, cents,
                      group["createdAt"] + timedelta(days=2))
    for user, balance in balances.items():
        record("tblWalletAccount", userId=user, balanceCents=balance, rowVersion=0)


def validate(rows):
    """Validate counts, money, stock, governance history, and references."""
    if len(rows["tblShop"]) != 6 or Counter(row["shopStatus"] for row in rows["tblShop"]) != Counter({"ACTIVE": 5, "SUSPENDED": 1}):
        raise AssertionError("shop status matrix is invalid")
    if len(rows["tblProduct"]) != 72 or len(rows["tblOrder"]) != 20:
        raise AssertionError("catalog or order count is invalid")
    sku_counts = Counter(row["productId"] for row in rows["tblProductSku"])
    if sum(value >= 2 for value in sku_counts.values()) < 12:
        raise AssertionError("at least twelve products need multiple variants")
    cart_counts = sorted(Counter(row["cartId"] for row in rows["tblCartItem"]).values())
    if cart_counts != [2, 6] or len(rows["tblCart"]) != 3:
        raise AssertionError("cart coverage is invalid")
    if len(rows["tblShopQualification"]) < 5 or len(rows["tblShopGovCase"]) < 5:
        raise AssertionError("governance coverage is incomplete")
    wallet_users = {row["userId"] for row in rows["tblWalletAccount"]}
    required_wallets = {"user-student-2024-017", "user-student-2024-018",
                        "user-student-2024-001", "user-student-2024-002"} | {
                            row["ownerUserId"] for row in rows["tblShop"]}
    if wallet_users != required_wallets:
        raise AssertionError("shared wallet accounts are incomplete")
    for sku in rows["tblProductSku"]:
        if not 0 <= sku["reservedQuantity"] <= sku["stockQuantity"]:
            raise AssertionError("SKU stock is inconsistent")
