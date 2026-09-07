"""商城批量样本：先建立关联并校验，再输出给统一装载器。"""
from collections import defaultdict
from datetime import timedelta
from decimal import Decimal

from shop_checks import validate
from shop_orders import build_orders


def generate(add, now):
    rows = defaultdict(list)

    def record(table, **fields):
        rows[table].append(fields)
        return fields

    shops = []
    categories = ("文具", "图书", "生活用品", "药品", "其他")
    for n in range(1, 46):
        category = categories[(n - 1) % 5]
        name = f"批量{category}店{n:03d}"
        status = "APPROVED" if n <= 30 else ("DRAFT", "PENDING", "REJECTED")[(n - 31) // 5]
        reviewed = status in ("APPROVED", "REJECTED")
        record("tblSellerApplication", applicationId=f"bulk-application-{n:03d}",
               applicantUserId=f"bulk-student-user-{n:04d}", shopName=name,
               description=f"校园{category}测试商店", category=category,
               contact=f"shop{n:03d}@example.invalid", applicationStatement="用于校园商城综合测试",
               applicationStatus=status, reviewReason="材料完整，审核通过" if n <= 30 else
               ("经营说明待完善" if status == "REJECTED" else None),
               reviewerUserId="bulk-admin-001" if reviewed else None,
               submittedAt=None if status == "DRAFT" else now - timedelta(days=60),
               reviewedAt=now - timedelta(days=59) if reviewed else None, rowVersion=int(reviewed))
        if n > 30:
            continue
        suspended = n > 27
        shops.append(record("tblShop", shopId=f"bulk-shop-{n:03d}",
                            ownerUserId=f"bulk-student-user-{n:04d}", shopName=name,
                            normalizedShopName=name.lower(), description=f"校园{category}测试商店",
                            category=category, contact=f"shop{n:03d}@example.invalid",
                            shopStatus="SUSPENDED" if suspended else "ACTIVE",
                            suspensionReason="测试暂停营业状态" if suspended else None,
                            suspendedByUserId="bulk-admin-001" if suspended else None,
                            suspendedAt=now - timedelta(days=1) if suspended else None,
                            rowVersion=int(suspended), createdAt=now - timedelta(days=59), updatedAt=now))
    products, skus, available = {}, {}, []
    for n in range(1, 601):
        shop = shops[(n - 1) // 20]
        local = (n - 1) % 20 + 1
        status = "ACTIVE" if local <= 14 else "DRAFT" if local <= 17 else "INACTIVE"
        name = f"{shop['category']}测试商品{n:04d}"
        product = record("tblProduct", productId=f"bulk-product-{n:04d}", shopId=shop["shopId"],
                         productName=name, normalizedProductName=name.lower(), category=shop["category"],
                         description=f"综合测试商品，编号{n:04d}", coverImageUrl=None,
                         productStatus=status, salesCount=0, rowVersion=0,
                         createdAt=now - timedelta(days=50), updatedAt=now)
        products[product["productId"]] = product
        for variant in (1, 2):
            # 每店第十四件商品用于零库存展示；第二规格覆盖停用状态。
            stock = 0 if local == 14 else 100 + n % 20
            sku = record("tblProductSku", skuId=f"bulk-sku-{n:04d}-{variant}",
                         productId=product["productId"], skuName=("标准款", "加量款")[variant - 1],
                         unitPrice=(Decimal(n % 80 + 1) + Decimal(variant * 25) / 100),
                         stockQuantity=stock, reservedQuantity=0,
                         isActive=not (local == 13 and variant == 2), rowVersion=0)
            skus[sku["skuId"]] = sku
            if shop["shopStatus"] == "ACTIVE" and status == "ACTIVE" and stock and sku["isActive"]:
                available.append((shop, product, sku))
    initial_stock = {key: value["stockQuantity"] for key, value in skus.items()}
    for n in range(1, 301):
        cart_id = f"bulk-cart-{n:04d}"
        record("tblCart", cartId=cart_id, userId=f"bulk-student-user-{n + 100:04d}", updatedAt=now)
        for variant in (0, 1):
            sku = available[((n - 1) * 2 + variant) % len(available)][2]
            record("tblCartItem", cartItemId=f"bulk-cartitem-{n:04d}-{variant + 1}",
                   cartId=cart_id, skuId=sku["skuId"], quantity=variant + 1,
                   rowVersion=0, createdAt=now - timedelta(hours=2), updatedAt=now)
    build_orders(record, now, available)
    validate(rows, initial_stock)
    for table, records in rows.items():
        for fields in records:
            add(table, **fields)
    return {table: len(records) for table, records in rows.items()}
