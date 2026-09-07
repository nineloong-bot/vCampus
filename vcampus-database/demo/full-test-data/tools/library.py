"""图书馆测试资料包含可借、借出、逾期、归还和遗失场景。"""
from datetime import date, timedelta


def generate(add, now):
    categories = ['计算机', '数学', '文学', '经济管理', '艺术', '外语', '历史', '自然科学']
    titles = ['基础教程', '案例实践', '方法与应用', '专题研究', '阅读指南']
    for i in range(1, 501):
        category = categories[(i-1)%8]
        # 生成满足校验位的十三位合成 ISBN。
        prefix = f'978990{i:06}'
        check = (10-sum(int(c)*(1 if j%2==0 else 3) for j,c in enumerate(prefix))%10)%10
        add('tblBook', bookId=f'bulk-book-{i:04}', isbn=prefix+str(check),
            title=f'{category}{titles[(i-1)%5]}·测试卷{i:03}', author=f'测试编写组{(i-1)%30+1:02}',
            publisher='校园测试出版社', publishDate=date(2020+i%6,1,1), category=category,
            description='合成资料，用于检索、分页、馆藏管理和借阅测试。', isActive=i<=490, rowVersion=0)
        for j in range(1, 5):
            # 每本保留至少两册可借；每条当前借阅对应唯一馆藏。
            loan_state = None
            if j == 1 and i <= 300:
                loan_state = 'ACTIVE' if i <= 200 else 'OVERDUE'
            elif j == 1 and i <= 400:
                loan_state = 'RETURNED'
            elif j == 1 and i <= 420:
                loan_state = 'LOST'
            state = 'BORROWED' if loan_state in ('ACTIVE','OVERDUE') else (
                'LOST' if loan_state=='LOST' else 'AVAILABLE')
            copy_id = f'bulk-copy-{i:04}-{j}'
            add('tblBookCopy', copyId=copy_id, bookId=f'bulk-book-{i:04}',
                barcode=f'TEST-LIB-{i:04}-{j}', locationCode=f'九龙湖-{(i-1)%5+1}层-{j}区',
                copyStatus=state, rowVersion=0)
            if loan_state:
                teacher = i%10==0
                borrower = f'bulk-teacher-{(i-1)%50+1:03}' if teacher else f'bulk-student-user-{i:04}'
                borrowed = now-timedelta(days=40 if loan_state!='ACTIVE' else 5)
                due = now+timedelta(days=20) if loan_state=='ACTIVE' else now-timedelta(days=5)
                add('tblBookLoan', loanId=f'bulk-loan-{i:04}', copyId=copy_id,
                    borrowerUserId=borrower, borrowedAt=borrowed, dueAt=due,
                    returnedAt=now-timedelta(days=8) if loan_state=='RETURNED' else None,
                    renewCount=0, loanStatus=loan_state, rowVersion=0)
