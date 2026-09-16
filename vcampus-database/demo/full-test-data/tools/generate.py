"""Build the deterministic release SQL and its review manifests."""
import json
import sys
from collections import Counter, defaultdict
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

import courses
import library
import major_transfer
import people
import shop

NOW = datetime(2026, 9, 16, 12)
BANNED = ("test", "测试", "demo", "演示", "fake", "sample", "bulk", "dummy", "example")


def literal(value):
    """Render one Python value as an Access SQL literal."""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, datetime):
        return f"#{value:%Y-%m-%d %H:%M:%S}#"
    if isinstance(value, date):
        return f"#{value:%Y-%m-%d}#"
    if isinstance(value, (int, float, Decimal)):
        return str(value)
    if not isinstance(value, str) or ";" in value or "\n" in value:
        raise ValueError(f"Unsupported SQL value: {value!r}")
    return "'" + value.replace("'", "''") + "'"


def _validate_text(rows):
    for table, table_rows in rows.items():
        for row in table_rows:
            for field, value in row.items():
                if isinstance(value, str) and any(marker in value.lower() for marker in BANNED):
                    raise AssertionError(f"Banned marker in {table}.{field}: {value}")


def _json(value):
    if isinstance(value, (date, datetime)):
        return value.isoformat()
    if isinstance(value, Decimal):
        return str(value)
    raise TypeError(type(value).__name__)


def _write_accounts(output, accounts, overdue):
    payload = {"initialPassword": people.PASSWORD, "accounts": accounts, "overdue": overdue}
    (output / "accounts.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, default=_json) + "\n", encoding="utf-8")
    lines = [
        "# 初始账号与关键场景", "", "所有账号初始密码均为 `123456`。学生首次登录必须修改密码。", "",
        "| 账号 | 角色 | 姓名/说明 | 用途 | 首次改密 |",
        "| --- | --- | --- | --- | --- |",
    ]
    lines.extend(f"| {a['login']} | {a['role']} | {a['name']} | {a['scenario']} | {a['first_change']} |"
                 for a in accounts)
    lines.extend(["", "## 图书逾期罚款账号", "",
                  "| 账号 | 姓名 | 图书 | 条码 | 应还日期 | 逾期天数 | 罚款 | 钱包余额（分） |",
                  "| --- | --- | --- | --- | --- | ---: | ---: | ---: |"])
    lines.extend(f"| {r['login']} | {r['name']} | {r['book']} | {r['barcode']} | "
                 f"{r['due_at']} | {r['overdue_days']} | {r['fine']} | {r['balance_cents']} |"
                 for r in overdue)
    (output / "账号清单.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    """Generate every business row without touching a live database."""
    if len(sys.argv) != 2:
        raise SystemExit("Usage: generate.py OUTPUT_DIRECTORY")
    output = Path(sys.argv[1])
    output.mkdir(parents=True, exist_ok=True)
    sql, counts, generated = [], Counter(), defaultdict(list)

    def add(table, **fields):
        if not fields or not table.startswith("tbl"):
            raise ValueError(f"Invalid row for {table}")
        if table == "tblNumberSequence" and fields.get("sequenceKey") == "CAMPUS_CARD_GLOBAL":
            sql.append("UPDATE tblNumberSequence SET currentValue=40,maxValue=9999,"
                       "rowVersion=0,updatedAt=#2026-09-16 12:00:00# "
                       "WHERE sequenceKey='CAMPUS_CARD_GLOBAL';")
        else:
            sql.append(f"INSERT INTO {table} ({','.join(fields)}) VALUES "
                       f"({','.join(literal(value) for value in fields.values())});")
        generated[table].append(fields)
        counts[table] += 1

    accounts = people.generate(add, NOW)
    courses.generate(add, NOW)
    major_transfer.generate(add, NOW)
    library.generate(add, NOW)
    shop.generate(add, NOW)

    people.validate_identity_fixture(generated)
    courses.validate_course_fixture(generated)
    major_transfer.validate_transfer_fixture(generated)
    shop.validate(generated)
    _validate_text(generated)
    overdue = library.overdue_manifest(generated, NOW)

    ordered_counts = dict(sorted(counts.items()))
    (output / "generated.sql").write_text("\n".join(sql) + "\n", encoding="utf-8")
    (output / "counts.json").write_text(
        json.dumps(ordered_counts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "counts.tsv").write_text(
        "\n".join(f"{table}\t{count}" for table, count in ordered_counts.items()) + "\n",
        encoding="utf-8")
    _write_accounts(output, accounts, overdue)
    scenarios = {"dataVersion": "2026-09-16", "generatedAt": NOW,
                 "openTerm": "2026-2027-1", "overdueAccounts": overdue,
                 "transferApplicants": [row["studentId"]
                                        for row in generated["tblMajorTransferApplication"]]}
    (output / "scenarios.json").write_text(
        json.dumps(scenarios, ensure_ascii=False, indent=2, default=_json) + "\n", encoding="utf-8")
    print(json.dumps(ordered_counts, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
