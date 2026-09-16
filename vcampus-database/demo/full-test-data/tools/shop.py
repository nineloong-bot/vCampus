"""对外展示用商城样本，覆盖买家、店主、治理和钱包结算流程。"""
from collections import defaultdict
from datetime import timedelta
from decimal import Decimal

from shop_checks import validate
from shop_orders import build_orders

SHOP_SPECS = (
    ("青禾文具铺", "文具", "bulk-student-user-0001", "ACTIVE"),
    ("拾光书屋", "图书", "bulk-student-user-0002", "ACTIVE"),
    ("梧桐生活馆", "生活用品", "bulk-student-user-0003", "ACTIVE"),
    ("行知运动小铺", "其他", "bulk-student-user-0004", "ACTIVE"),
    ("麦香校园食坊", "食品", "bulk-student-user-0005", "ACTIVE"),
    ("晚风杂货铺", "其他", "bulk-student-user-0006", "SUSPENDED"),
)
PRODUCTS = (
    ("A5方格笔记本", "文具", "stationery/notebook-1", 8.80, ("米白", "浅蓝")),
    ("黑色按动中性笔套装", "文具", "stationery/writing-1", 12.90, ("黑色", "蓝色")),
    ("透明文件夹组合", "文具", "stationery/ruler-1", 15.50, ("A4", "A5")),
    ("彩色荧光标记笔", "文具", "stationery/marker-1", 18.00, ("基础色", "柔和色")),
    ("《乡土中国》", "图书", "books/reading-1", 36.00, ("平装", "精装")),
    ("大学生数学手册", "图书", "books/reference-1", 29.80, ("新版", "便携版")),
    ("文学经典导读", "图书", "books/literature-1", 42.00, ("平装", "收藏版")),
    ("课程复习资料册", "图书", "books/textbook-1", 24.50, ("黑白", "彩印")),
    ("陶瓷马克杯", "生活用品", "daily/drinkware-1", 32.00, ("350mL", "500mL")),
    ("桌面分格收纳盒", "生活用品", "daily/storage-1", 26.80, ("三格", "五格")),
    ("轻便帆布托特包", "生活用品", "daily/care-1", 45.00, ("米白", "墨绿")),
    ("宿舍清洁套装", "生活用品", "daily/cleaning-1", 39.90, ("基础包", "加量包")),
    ("纯棉运动短袖", "其他", "other/sports-1", 59.00, ("M码", "L码")),
    ("校园运动袜三双装", "其他", "other/sports-1", 25.00, ("白色", "灰色")),
    ("校园随身U盘", "其他", "other/digital-1", 49.90, ("64GB", "128GB")),
    ("独立包装燕麦饼干", "食品", "other/general-1", 16.80, ("原味", "坚果味")),
    ("蜂蜜柠檬茶包", "食品", "other/general-1", 22.00, ("10袋", "20袋")),
    ("校园纪念明信片", "其他", "other/gift-1", 9.90, ("单张", "套装")),
)
IMAGE_IDS = {
    "stationery/notebook-1": "book", "stationery/writing-1": "pen",
    "stationery/ruler-1": "box", "stationery/marker-1": "pen",
    "books/reading-1": "book", "books/reference-1": "book",
    "books/literature-1": "book", "books/textbook-1": "book",
    "daily/drinkware-1": "cup", "daily/storage-1": "box",
    "daily/care-1": "bag", "daily/cleaning-1": "box",
    "other/sports-1": "shirt", "other/digital-1": "box",
    "other/general-1": "box", "other/gift-1": "bag",
}


def generate(add, now):
    rows = defaultdict(list)

    def record(table, **fields):
        rows[table].append(fields)
        return fields

    applications = (
        ("bulk-seller-application-001", "bulk-student-user-0001", "青禾文具铺", "APPROVED", "材料齐全，经营范围明确"),
        ("bulk-seller-application-002", "bulk-student-user-0002", "拾光书屋", "APPROVED", "材料齐全，经营范围明确"),
        ("bulk-seller-application-003", "bulk-student-user-0003", "梧桐生活馆", "APPROVED", "材料齐全，经营范围明确"),
        ("bulk-seller-application-004", "bulk-student-user-0004", "行知运动小铺", "APPROVED", "材料齐全，经营范围明确"),
        ("bulk-seller-application-005", "bulk-student-user-0005", "麦香校园食坊", "APPROVED", "食品经营模拟资质已提交"),
        ("bulk-seller-application-006", "bulk-student-user-0006", "晚风杂货铺", "APPROVED", "材料齐全，后续暂停营业"),
        ("bulk-seller-application-007", "bulk-student-user-0031", "晨曦手作小铺", "PENDING", None),
        ("bulk-seller-application-008", "bulk-student-user-0032", "知行书角", "REJECTED", "营业执照编号未填写完整，请核对后重新提交"),
    )
    for app_id, user_id, name, status, reason in applications:
        reviewed = status in ("APPROVED", "REJECTED")
        record("tblSellerApplication", applicationId=app_id, applicantUserId=user_id,
               shopName=name, description=f"为师生提供{name}相关商品与服务。",
               category="校园生活", contact=f"merchant-{app_id[-3:]}@example.invalid",
               applicationStatement="使用课程模拟主体资料申请校园商城经营资格。",
               applicationStatus=status, reviewReason=reason,
               reviewerUserId="bulk-admin-001" if reviewed else None,
               submittedAt=now - timedelta(days=30),
               reviewedAt=now - timedelta(days=29) if reviewed else None,
               rowVersion=int(reviewed))

    shops = []
    for index, (name, category, owner, status) in enumerate(SHOP_SPECS, 1):
        shops.append(record("tblShop", shopId=f"bulk-shop-{index:03d}", ownerUserId=owner,
                            shopName=name, normalizedShopName=name.lower(),
                            description=f"{name}为校园师生提供{category}精选商品，支持校内配送。",
                            category=category, contact=f"merchant-{index:03d}@example.invalid",
                            shopStatus=status,
                            suspensionReason="店铺经营资料待复核，暂缓营业" if status == "SUSPENDED" else None,
                            suspendedByUserId="bulk-admin-001" if status == "SUSPENDED" else None,
                            suspendedAt=now - timedelta(days=2) if status == "SUSPENDED" else None,
                            rowVersion=int(status == "SUSPENDED"),
                            createdAt=now - timedelta(days=30), updatedAt=now))

    products, skus, available = {}, {}, []
    product_number = 0
    for shop_index, shop in enumerate(shops, 1):
        for local in range(1, 13):
            product_number += 1
            name, category, image, price, variants = PRODUCTS[(product_number - 1) % len(PRODUCTS)]
            if product_number > len(PRODUCTS):
                name = f"{name} · {shop['shopName']}"
            status = "ACTIVE" if local <= 8 else "DRAFT" if local in (9, 12) else "INACTIVE"
            if shop["shopStatus"] == "SUSPENDED":
                status = "INACTIVE"
            catalog_category = "licensed" if category == "食品" else "ordinary"
            image_id = IMAGE_IDS[image]
            product = record("tblProduct", productId=f"bulk-product-{product_number:03d}",
                             shopId=shop["shopId"], productName=name,
                             normalizedProductName=name.lower(), category=catalog_category,
                             description=f"适合校园学习与生活的{name}，规格清晰，支持校内自提。",
                             coverImageUrl=None, productStatus=status,
                             salesCount=0, rowVersion=0,
                             createdAt=now - timedelta(days=20), updatedAt=now)
            products[product["productId"]] = product
            for variant_index, variant in enumerate(variants, 1):
                stock = 0 if local == 10 and variant_index == 1 else (2 if local == 11 else 24 + variant_index * 6)
                active = not (local == 9 and variant_index == 2)
                sku = record("tblProductSku", skuId=f"bulk-sku-{product_number:03d}-{variant_index}",
                             productId=product["productId"], skuName=variant,
                             unitPrice=Decimal(str(price + (variant_index - 1) * 3.00)),
                             stockQuantity=stock, reservedQuantity=0, isActive=active, rowVersion=0)
                skus[sku["skuId"]] = sku
                if shop["shopStatus"] == "ACTIVE" and status == "ACTIVE" and active and stock > 0:
                    available.append((shop, product, sku))
            default_sku = f"bulk-sku-{product_number:03d}-1"
            record("tblProductCatalog", productId=product["productId"], defaultSkuId=default_sku,
                   imageId=image_id, isDeleted=product_number == 72)
            if product_number == 72:
                record("tblSkuDraftFields", skuId=default_sku, priceMissing=False, stockMissing=False)

    _governance(record, shops, products, now)
    initial_stock = {key: value["stockQuantity"] for key, value in skus.items()}
    for number, user_index in enumerate((101, 102, 103, 104, 105), 1):
        cart_id = f"bulk-cart-demo-{number:02d}"
        record("tblCart", cartId=cart_id, userId=f"bulk-student-user-{user_index:04d}", updatedAt=now)
        for offset in range(2 if number <= 4 else 0):
            sku = available[(number * 3 + offset) % len(available)][2]
            record("tblCartItem", cartItemId=f"bulk-cartitem-demo-{number:02d}-{offset + 1}",
                   cartId=cart_id, skuId=sku["skuId"], quantity=offset + 1,
                   rowVersion=0, createdAt=now - timedelta(hours=2), updatedAt=now)

    build_orders(record, now, available, products, skus, shops)
    _wallet(record, now, shops, rows)
    validate(rows, initial_stock)
    for table, records in rows.items():
        for fields in records:
            add(table, **fields)
    return {table: len(records) for table, records in rows.items()}


def _governance(record, shops, products, now):
    record("tblShopGovApplication", applicationId="bulk-gov-application-001",
           subjectName="麦香校园食坊", licenseNumber="SIM-FOOD-2026-001")
    qualification_specs = (
        ("bulk-qualification-001", 5, "APPROVED", "SPECIAL", "SIM-FOOD-2026-001", now + timedelta(days=180), "审核通过，食品商品可按规则上架"),
        ("bulk-qualification-002", 1, "PENDING", "SPECIAL", "SIM-SPECIAL-2026-002", now + timedelta(days=240), None),
        ("bulk-qualification-003", 2, "REJECTED", "SPECIAL", "SIM-SPECIAL-2026-003", now + timedelta(days=120), "资质证明照片缺少有效期，请补充后重新提交"),
        ("bulk-qualification-004", 4, "EXPIRED", "SPECIAL", "SIM-SPECIAL-2025-004", now - timedelta(days=10), "模拟专项资质已到期，相关商品暂停售"),
    )
    for qid, shop_index, status, kind, number, expires, reason in qualification_specs:
        record("tblShopQualification", qualificationId=qid, shopId=shops[shop_index - 1]["shopId"],
               licenseType=kind, licenseNumber=number, expiresOn=expires,
               qualificationStatus=status, reviewReason=reason, submittedAt=now - timedelta(days=20))
    restricted = list(products.values())
    record("tblShopProductRestriction", productId=restricted[1]["productId"], emergencyBlocked=True,
           qualificationBlocked=False, expiryRestoreEligible=False)
    record("tblShopProductRestriction", productId=restricted[52]["productId"], emergencyBlocked=False,
           qualificationBlocked=True, expiryRestoreEligible=True)
    cases = (
        ("举报商品规格描述不完整", restricted[1]["productId"], shops[0]["shopId"], "商品介绍未说明套装内水笔数量，请补充信息后申请复核。", "PENDING"),
        ("举报商品信息已处理", restricted[2]["productId"], shops[0]["shopId"], "文件夹材质描述已补充，管理员完成信息复核。", "RESOLVED"),
        ("店铺经营警告", shops[1]["shopId"], shops[1]["shopId"], "店铺需在七日内补充模拟经营主体资料。", "RESOLVED"),
        ("商品紧急下架", restricted[1]["productId"], shops[0]["shopId"], "发现商品介绍与实际规格不一致，先行紧急下架整改。", "ACTIONED"),
        ("店铺暂停营业", shops[5]["shopId"], shops[5]["shopId"], "暂停营业通知：经营资料待复核，完成审核后申请恢复。", "ACTIONED"),
        ("店铺恢复营业申请", shops[5]["shopId"], shops[5]["shopId"], "已补充经营资料，申请解除店铺暂停限制。", "PENDING"),
        ("商品整改复核申请", restricted[1]["productId"], shops[0]["shopId"], "已补充规格和图片说明，申请解除管理员紧急下架。", "PENDING"),
    )
    for index, (kind, object_id, shop_id, explanation, status) in enumerate(cases, 1):
        case_id = f"bulk-gov-case-{index:03d}"
        record("tblShopGovCase", caseId=case_id,
               actorId="bulk-admin-001" if status != "PENDING" else shops[0]["ownerUserId"],
               caseKind=kind, objectId=object_id, shopId=shop_id, reason=kind,
               explanation=explanation, caseStatus=status,
               resultText="已记录处理结果" if status != "PENDING" else None,
               createdAt=now - timedelta(days=index))
        record("tblShopGovAudit", auditId=f"bulk-gov-audit-{index:03d}", actorId="bulk-admin-001",
               objectId=object_id, actionName=kind, reason=explanation,
               occurredAt=now - timedelta(days=index),
               beforeState="ACTIVE" if status != "PENDING" else "PENDING", afterState=status,
               linkedId=case_id)


def _wallet(record, now, shops, rows):
    buyers = [f"bulk-student-user-{index:04d}" for index in (101, 102, 103, 104, 105)]
    topups = ((buyers[0], 50000), (buyers[1], 500), (buyers[3], 20000), (buyers[4], 10000))
    for index, (user, amount) in enumerate(topups, 1):
        operation = f"bulk-wallet-topup-{index:03d}"
        record("tblWalletOperation", operationId=operation,
               businessKey=f"R:DEMO:RECHARGE:{index:03d}", operationType="RECHARGE",
               actorId=user, peerId="bulk-admin-001", orderKey="-", amountCents=amount,
               balanceAfter=amount, createdAt=now - timedelta(days=30 - index))
        record("tblWalletEntry", entryId=f"bulk-wallet-entry-{index:03d}-u", operationId=operation,
               accountKind="USER", accountKey=user, deltaCents=amount)
        record("tblWalletEntry", entryId=f"bulk-wallet-entry-{index:03d}-s", operationId=operation,
               accountKind="SYSTEM", accountKey="PLATFORM", deltaCents=-amount)
    users = set(buyers) | {shop["ownerUserId"] for shop in shops}
    balances = {user: 0 for user in users}
    for operation in sorted(rows["tblWalletOperation"], key=lambda value: (value["createdAt"], value["operationId"])):
        user_entries = [entry for entry in rows["tblWalletEntry"]
                        if entry["operationId"] == operation["operationId"]
                        and entry["accountKind"] == "USER"]
        actor = operation["actorId"]
        if user_entries:
            balances[actor] = balances.get(actor, 0) + sum(entry["deltaCents"] for entry in user_entries)
            operation["balanceAfter"] = balances[actor]
    for user, balance in balances.items():
        assert balance >= 0, (user, balance)
        record("tblWalletAccount", userId=user, balanceCents=balance, rowVersion=0)
