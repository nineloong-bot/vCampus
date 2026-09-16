"""Realistic library catalog and traceable overdue scenarios."""
from datetime import date, timedelta
from decimal import Decimal

BOOKS = (
    ("9787111544937", "深入理解计算机系统", "兰德尔·布莱恩特", "机械工业出版社", "计算机"),
    ("9787302423287", "数据结构", "严蔚敏", "清华大学出版社", "计算机"),
    ("9787111608365", "算法导论", "托马斯·科尔曼", "机械工业出版社", "计算机"),
    ("9787302330646", "数据库系统概论", "王珊", "高等教育出版社", "计算机"),
    ("9787040458312", "数学分析", "华东师范大学数学系", "高等教育出版社", "数学"),
    ("9787040379105", "高等代数", "北京大学数学系", "高等教育出版社", "数学"),
    ("9787040511482", "概率论与数理统计", "盛骤", "高等教育出版社", "数学"),
    ("9787302499761", "机器学习", "周志华", "清华大学出版社", "人工智能"),
    ("9787115428028", "计算机网络", "谢希仁", "电子工业出版社", "计算机"),
    ("9787111612882", "现代操作系统", "安德鲁·塔嫩鲍姆", "机械工业出版社", "计算机"),
    ("9787302517609", "软件工程", "张海藩", "清华大学出版社", "软件工程"),
    ("9787030186401", "数值分析", "李庆扬", "科学出版社", "数学"),
)


def generate(add, now):
    """Generate books, copies, policies, active loans, returns, and reservations."""
    add("tblLibraryPolicy", policyId="policy-student", roleCode="STUDENT",
        maxActiveLoans=5, loanDays=30, maxRenewals=1, renewalDays=15,
        reserveDays=3, firstTierDays=7, secondTierDays=30,
        firstDailyFine=Decimal("0.50"), secondDailyFine=Decimal("1.00"),
        thirdDailyFine=Decimal("2.00"), minorDamageFine=Decimal("10.00"),
        majorDamageFine=Decimal("50.00"), lostFine=Decimal("100.00"), rowVersion=0)
    add("tblLibraryPolicy", policyId="policy-teacher", roleCode="TEACHER",
        maxActiveLoans=10, loanDays=60, maxRenewals=2, renewalDays=30,
        reserveDays=5, firstTierDays=7, secondTierDays=30,
        firstDailyFine=Decimal("0.50"), secondDailyFine=Decimal("1.00"),
        thirdDailyFine=Decimal("2.00"), minorDamageFine=Decimal("10.00"),
        majorDamageFine=Decimal("50.00"), lostFine=Decimal("100.00"), rowVersion=0)
    for index, (isbn, title, author, publisher, category) in enumerate(BOOKS, 1):
        book_id = f"book-{index:03d}"
        add("tblBook", bookId=book_id, isbn=isbn, title=title, author=author,
            publisher=publisher, publishDate=date(2020 + index % 5, index % 12 + 1, 1),
            category=category, description=f"{title}馆藏版本，供课程学习与专题阅读。",
            isActive=True, rowVersion=0)
        for copy in range(1, 4):
            status = "AVAILABLE"
            if (index, copy) in {(1, 1), (2, 1), (3, 1), (4, 1)}:
                status = "BORROWED"
            if (index, copy) == (12, 3):
                status = "LOST"
            add("tblBookCopy", copyId=f"copy-{index:03d}-{copy}", bookId=book_id,
                barcode=f"LIB{index:04d}{copy}", locationCode=f"九龙湖图书馆{index % 4 + 2}层A区",
                copyStatus=status, rowVersion=0)
    _loan(add, "loan-overdue-short", "copy-001-1", "user-student-2024-001",
          now - timedelta(days=34), now - timedelta(days=4), None, 0, "OVERDUE", Decimal("2.00"))
    _loan(add, "loan-overdue-long", "copy-002-1", "user-student-2024-002",
          now - timedelta(days=42), now - timedelta(days=12), None, 0, "OVERDUE", Decimal("8.50"))
    _loan(add, "loan-current", "copy-003-1", "user-student-2025-001",
          now - timedelta(days=8), now + timedelta(days=22), None, 0, "ACTIVE", Decimal("0"))
    _loan(add, "loan-renewed", "copy-004-1", "user-student-2025-002",
          now - timedelta(days=35), now + timedelta(days=10), None, 1, "ACTIVE", Decimal("0"))
    _loan(add, "loan-returned", "copy-005-1", "user-student-2024-003",
          now - timedelta(days=25), now + timedelta(days=5), now - timedelta(days=2),
          0, "RETURNED", Decimal("0"))
    add("tblBookReservation", reservationId="reservation-001", copyId="copy-006-1",
        bookId="book-006", userId="user-student-2026-001", reserverRoleCode="STUDENT",
        reservedAt=now - timedelta(days=1), queueOrder=1, reservationStatus="WAITING",
        readyAt=None, expiresAt=None, rowVersion=0)
    add("tblBookReservation", reservationId="reservation-002", copyId="copy-007-1",
        bookId="book-007", userId="user-student-2026-002", reserverRoleCode="STUDENT",
        reservedAt=now - timedelta(days=2), queueOrder=1, reservationStatus="READY",
        readyAt=now - timedelta(hours=4), expiresAt=now + timedelta(days=3), rowVersion=0)


def _loan(add, loan_id, copy_id, user_id, borrowed, due, returned, renewals, status, fine):
    add("tblBookLoan", loanId=loan_id, copyId=copy_id, borrowerUserId=user_id,
        borrowedAt=borrowed, dueAt=due, returnedAt=returned, renewCount=renewals,
        loanStatus=status, borrowerRoleCode="STUDENT", overdueFine=fine,
        damageFine=Decimal("0"), returnCondition="NORMAL", rowVersion=0)


def overdue_manifest(rows, now):
    """Return human-readable overdue account details for manual verification."""
    users = {row["userId"]: row for row in rows["tblUser"]}
    students = {row["userId"]: row for row in rows["tblStudent"]}
    copies = {row["copyId"]: row for row in rows["tblBookCopy"]}
    books = {row["bookId"]: row for row in rows["tblBook"]}
    wallets = {row["userId"]: row["balanceCents"] for row in rows["tblWalletAccount"]}
    result = []
    for loan in rows["tblBookLoan"]:
        if loan["loanStatus"] != "OVERDUE":
            continue
        user_id = loan["borrowerUserId"]
        copy = copies[loan["copyId"]]
        result.append(dict(login=users[user_id]["loginId"], name=students[user_id]["studentName"],
                           book=books[copy["bookId"]]["title"], barcode=copy["barcode"],
                           due_at=loan["dueAt"].isoformat(), overdue_days=(now - loan["dueAt"]).days,
                           fine=str(loan["overdueFine"]), balance_cents=str(wallets.get(user_id, 0))))
    return result
