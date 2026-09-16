"""商城展示数据的主键、金额、库存、治理和钱包一致性校验。"""
from collections import Counter, defaultdict
from decimal import Decimal


def validate(rows, initial_stock):
    def index(table, key):
        values = {row[key]: row for row in rows[table]}
        assert len(values) == len(rows[table]), (table, "重复主键")
        return values

    shops = index("tblShop", "shopId")
    products = index("tblProduct", "productId")
    skus = index("tblProductSku", "skuId")
    groups = index("tblOrderGroup", "orderGroupId")
    orders = index("tblOrder", "orderId")
    payments = index("tblPayment", "paymentId")
    wallet_accounts = {row["userId"]: row for row in rows["tblWalletAccount"]}
    users = set(wallet_accounts)
    assert len(shops) == 6 and len(products) == 72 and len(skus) == 144
    assert {shop["shopStatus"] for shop in shops.values()} == {"ACTIVE", "SUSPENDED"}
    assert all("批量" not in product["productName"] for product in products.values())
    assert all(sku["reservedQuantity"] <= sku["stockQuantity"] for sku in skus.values())
    for product in products.values():
        product_skus = [sku for sku in skus.values() if sku["productId"] == product["productId"]]
        catalog = next(row for row in rows["tblProductCatalog"] if row["productId"] == product["productId"])
        assert any(sku["isActive"] for sku in product_skus) or product["productStatus"] != "ACTIVE"
        assert catalog["defaultSkuId"] in {sku["skuId"] for sku in product_skus}
    line_totals, order_quantities, reservation_quantities = defaultdict(Decimal), Counter(), Counter()
    for item in rows["tblOrderItem"]:
        order, sku = orders[item["orderId"]], skus[item["skuId"]]
        assert products[sku["productId"]]["shopId"] == order["shopId"]
        assert item["lineAmount"] == item["unitPrice"] * item["quantity"]
        line_totals[item["orderId"]] += item["lineAmount"]
        order_quantities[(order["orderGroupId"], item["skuId"])] += item["quantity"]
    for order in orders.values():
        assert line_totals[order["orderId"]] == order["orderAmount"]
        assert groups[order["orderGroupId"]]["totalAmount"] == order["orderAmount"]
    for payment in payments.values():
        group = groups[payment["orderGroupId"]]
        assert payment["amount"] == group["totalAmount"]
        assert payment["paymentStatus"] in ("PENDING", "SUCCEEDED", "CANCELLED", "EXPIRED")
    for reservation in rows["tblInventoryReservation"]:
        payment = payments[reservation["paymentId"]]
        reservation_quantities[(payment["orderGroupId"], reservation["skuId"])] += reservation["quantity"]
        if reservation["reservationStatus"] == "ACTIVE":
            assert payment["paymentStatus"] == "PENDING"
            skus[reservation["skuId"]]["reservedQuantity"] += reservation["quantity"]
        elif reservation["reservationStatus"] == "CONSUMED":
            assert payment["paymentStatus"] == "SUCCEEDED"
            skus[reservation["skuId"]]["stockQuantity"] -= reservation["quantity"]
        else:
            assert payment["paymentStatus"] in ("CANCELLED", "EXPIRED", "SUCCEEDED")
    for sku_id, sku in skus.items():
        assert sku["stockQuantity"] == initial_stock[sku_id] - sum(
            row["quantity"] for row in rows["tblInventoryReservation"]
            if row["skuId"] == sku_id and row["reservationStatus"] == "CONSUMED")
        assert sku["reservedQuantity"] == sum(
            row["quantity"] for row in rows["tblInventoryReservation"]
            if row["skuId"] == sku_id and row["reservationStatus"] == "ACTIVE")
    assert order_quantities == reservation_quantities
    assert all(row["userId"] in users for row in rows["tblWalletAccount"])
    for entry in rows["tblWalletEntry"]:
        assert entry["operationId"] in {row["operationId"] for row in rows["tblWalletOperation"]}
    for user, account in wallet_accounts.items():
        assert account["balanceCents"] == sum(entry["deltaCents"] for entry in rows["tblWalletEntry"]
                                               if entry["accountKind"] == "USER" and entry["accountKey"] == user)
    assert all(operation["balanceAfter"] >= 0 for operation in rows["tblWalletOperation"]), [
        operation for operation in rows["tblWalletOperation"] if operation["balanceAfter"] < 0]
    assert len(rows["tblWalletEscrow"]) == 12
    assert {row["qualificationStatus"] for row in rows["tblShopQualification"]} == {"APPROVED", "PENDING", "REJECTED", "EXPIRED"}
    assert len(rows["tblShopGovCase"]) >= 5
    assert Counter(row["orderStatus"] for row in orders.values()) == {
        "PENDING_PAYMENT": 4, "PAID": 3, "SHIPPED": 3,
        "COMPLETED": 4, "PREPARING": 1, "CANCELLED": 3,
    }
