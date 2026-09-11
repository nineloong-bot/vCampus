"""生成可审查的 SQL、账号清单与数据数量，不接触正在使用的数据库。"""
import json
import sys
from collections import Counter
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
import people
import library
import courses
import shop


def literal(value):
    if value is None:
        return 'NULL'
    if isinstance(value, bool):
        return 'TRUE' if value else 'FALSE'
    if isinstance(value, datetime):
        return '#' + value.strftime('%Y-%m-%d %H:%M:%S') + '#'
    if isinstance(value, date):
        return '#' + value.isoformat() + '#'
    if isinstance(value, (int, float, Decimal)):
        return str(value)
    assert ';' not in value and '\n' not in value
    return "'" + value.replace("'", "''") + "'"


def main():
    output = Path(sys.argv[1])
    output.mkdir(parents=True, exist_ok=True)
    now = datetime(2026, 9, 7, 12)
    rows, counts = [], Counter()

    def add(table, **fields):
        assert fields and table.startswith('tbl')
        rows.append(f"INSERT INTO {table} ({','.join(fields)}) VALUES "
                    f"({','.join(literal(v) for v in fields.values())});")
        counts[table] += 1

    accounts = people.generate(add, now)
    courses.generate(add, now)
    library.generate(add, now)
    shop.generate(add, now)
    # 同步号码分配器，后续新增学生不会与批量档案冲突。
    rows.append("UPDATE tblNumberSequence SET currentValue=1000 WHERE sequenceKey='CAMPUS_CARD_GLOBAL';")
    for major in range(801, 811):
        for cohort in range(2023, 2027):
            add('tblNumberSequence', sequenceKey=f'STUDENT_NUMBER:{major}:{cohort % 100:02}:1',
                currentValue=25, maxValue=99, rowVersion=0, updatedAt=now)
    # 原始演示账户在独立库中也统一密码，避免账号清单再次出现歧义。
    credential = people.credentials('baseline-demo')
    rows.append('UPDATE tblUser SET '+','.join(f'{k}={literal(v)}' for k,v in credential.items())
                +",mustChangePassword=FALSE,failedLoginCount=0,lockedUntil=NULL WHERE userId NOT LIKE 'bulk-%';")
    (output/'generated.sql').write_text('\n'.join(rows)+'\n', encoding='utf-8')
    (output/'counts.json').write_text(json.dumps(counts,ensure_ascii=False,indent=2),encoding='utf-8')
    (output/'counts.tsv').write_text('\n'.join(f'{k}\t{v}' for k,v in counts.items()),encoding='utf-8')
    (output/'accounts.json').write_text(json.dumps(accounts,ensure_ascii=False,indent=2),encoding='utf-8')
    text = ['全模块合成测试账号；密码统一 Test12345。学生登录用一卡通号。',
            '账号 | 密码 | 角色 | 姓名/说明 | 场景 | 首次改密']
    text += [' | '.join(a.values()) for a in accounts]
    text += ['原有演示账号 ADMIN、TEACHER01、213230001、SHOPOWNER、SHOPDRAFT、SHOPPENDING',
             '在本独立测试库中密码也统一为 Test12345。']
    (output/'测试账号清单.txt').write_text('\n'.join(text)+'\n',encoding='utf-8-sig')
    print(json.dumps(counts,ensure_ascii=False,indent=2))


if __name__ == '__main__':
    main()
