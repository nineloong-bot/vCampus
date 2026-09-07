"""校验商城数据的金额、状态、外键与库存守恒。"""
from collections import Counter, defaultdict
from decimal import Decimal


def validate(rows, initial_stock):
    def index(table, key):
        result = {row[key]: row for row in rows[table]}
        assert len(result) == len(rows[table]), (table, "重复主键")
        assert all(value.startswith("bulk-") and len(value) <= 36 for value in result)
        return result

    shops = index("tblShop", "shopId")
    products = index("tblProduct", "productId")
    skus = index("tblProductSku", "skuId")
    groups = index("tblOrderGroup", "orderGroupId")
    orders = index("tblOrder", "orderId")
    payments = index("tblPayment", "paymentId")
    carts = index("tblCart", "cartId")
    reserved, consumed, sales = Counter(), Counter(), Counter()
    line_totals, group_totals = defaultdict(Decimal), defaultdict(Decimal)
    ordered_quantities, reservation_quantities = Counter(), Counter()
    for item in rows["tblOrderItem"]:
        order, sku = orders[item["orderId"]], skus[item["skuId"]]
        assert products[sku["productId"]]["shopId"] == order["shopId"]
        assert item["lineAmount"] == item["unitPrice"] * item["quantity"]
        line_totals[item["orderId"]] += item["lineAmount"]
        ordered_quantities[(order["orderGroupId"], item["skuId"])] += item["quantity"]
        if order["orderStatus"] in ("PAID", "PREPARING", "SHIPPED", "COMPLETED"):
            sales[sku["productId"]] += item["quantity"]
    for order in orders.values():
        assert line_totals[order["orderId"]] == order["orderAmount"]
        expected = "PAID" if order["orderStatus"] in ("PREPARING", "SHIPPED") else order["orderStatus"]
        assert groups[order["orderGroupId"]]["groupStatus"] == expected
        group_totals[order["orderGroupId"]] += order["orderAmount"]
    for payment in payments.values():
        group = groups[payment["orderGroupId"]]
        assert payment["amount"] == group["totalAmount"] == group_totals[group["orderGroupId"]]
        assert (payment["paymentStatus"] == "SUCCEEDED") == (payment["successfulChannel"] is not None)
    for reservation in rows["tblInventoryReservation"]:
        sku = skus[reservation["skuId"]]
        payment = payments[reservation["paymentId"]]
        reservation_quantities[(payment["orderGroupId"], reservation["skuId"])] += reservation["quantity"]
        status = reservation["reservationStatus"]
        if status == "ACTIVE":
            assert payment["paymentStatus"] == "PENDING"
            assert sku["isActive"] and products[sku["productId"]]["productStatus"] == "ACTIVE"
            assert shops[products[sku["productId"]]["shopId"]]["shopStatus"] == "ACTIVE"
            assert reservation["releasedAt"] is None
            reserved[reservation["skuId"]] += reservation["quantity"]
        elif status == "CONSUMED":
            assert payment["paymentStatus"] == "SUCCEEDED"
            consumed[reservation["skuId"]] += reservation["quantity"]
        else:
            assert payment["paymentStatus"] in ("CANCELLED", "EXPIRED")
        if payment["paymentStatus"] == "EXPIRED":
            assert payment["completedAt"] == reservation["releasedAt"] > reservation["expiresAt"]
    for sku_id, sku in skus.items():
        assert sku["reservedQuantity"] == reserved[sku_id]
        assert sku["stockQuantity"] == initial_stock[sku_id] - consumed[sku_id]
        assert 0 <= sku["reservedQuantity"] <= sku["stockQuantity"]
    assert ordered_quantities == reservation_quantities
    assert len({(item["cartId"], item["skuId"]) for item in rows["tblCartItem"]}) == 600
    for product in products.values():
        assert product["salesCount"] == sales[product["productId"]]
        assert product["category"] == shops[product["shopId"]]["category"]
    for item in rows["tblCartItem"]:
        assert item["cartId"] in carts and item["skuId"] in skus and item["quantity"] > 0
    for attempt in rows["tblPaymentAttempt"]:
        assert attempt["paymentId"] in payments and attempt["completedAt"] >= attempt["createdAt"]
    assert len(shops) == 30 and len(products) == 600 and len(skus) == 1200
    assert len(groups) == len(orders) == len(payments) == 600
    assert Counter(order["orderStatus"] for order in orders.values()) == dict.fromkeys(
        ("PENDING_PAYMENT", "PAID", "PREPARING", "SHIPPED", "COMPLETED", "CANCELLED"), 100)
