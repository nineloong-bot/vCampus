"""合成账户与学籍：独立密码盐，院系、班级及学号保持对应。"""
import hashlib
import base64
from datetime import date

PASSWORD = 'Test12345'


def credentials(key):
    salt = hashlib.sha256(('vcampus-fixture:' + key).encode()).digest()[:16]
    value = hashlib.pbkdf2_hmac('sha256', PASSWORD.encode(), salt, 120000)
    return dict(passwordHash=base64.b64encode(value).decode(),
                passwordSalt=base64.b64encode(salt).decode(), passwordIterations=120000)


def generate(add, now):
    accounts = []

    def user(uid, login, role, name, scenario, initial=False):
        add('tblUser', userId=uid, loginId=login, **credentials(uid), roleCode=role,
            accountStatus='ACTIVE', mustChangePassword=initial, failedLoginCount=0,
            lockedUntil=None, lastLoginAt=None, rowVersion=0, createdAt=now, updatedAt=now)
        accounts.append(dict(login=login, password=PASSWORD, role=role, name=name,
                             scenario=scenario, first_change='是' if initial else '否'))

    user('bulk-admin-001', 'TESTADMIN', 'ADMIN', '全模块测试管理员', '管理全部测试数据')
    for i in range(1, 51):
        user(f'bulk-teacher-{i:03}', f'TESTTEACHER{i:03}', 'TEACHER', f'测试教师{i:03}',
             '课程管理、图书借阅、普通买家')
    departments = ['计算机', '数学', '外国语', '经济管理', '艺术设计']
    majors = ['软件工程', '计算机科学', '数学应用', '统计学', '英语', '日语',
              '经济学', '管理学', '视觉传达', '产品设计']
    for i, name in enumerate(departments, 1):
        add('tblDepartment', departmentId=f'bulk-dept-{i:02}', departmentCode=f'TEST{i:02}',
            departmentName=f'{name}学院（测试）', isActive=True, rowVersion=0)
    for i, name in enumerate(majors, 1):
        add('tblMajor', majorId=f'bulk-major-{i:02}', departmentId=f'bulk-dept-{(i+1)//2:02}',
            majorCode=str(800+i), majorName=f'{name}（测试）', isActive=True, rowVersion=0)
        for j in (1, 2):
            add('tblClass', classId=f'bulk-class-{i:02}-{j}', majorId=f'bulk-major-{i:02}',
                classCode=f'{800+i}-2026-{j:02}', className=f'{800+i}26{j}班',
                enrollmentYear=2026, classNumber=j, isActive=True, rowVersion=0)
    surnames = '赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨'
    names = ['明轩', '雨桐', '子涵', '思远', '欣然', '浩宇', '若宁', '文博']
    for i in range(1, 1001):
        major, class_no, serial = (i-1)//100+1, (i-1)%100//50+1, (i-1)%50+1
        login, uid = f'21326{i:04}', f'bulk-student-user-{i:04}'
        name = surnames[(i-1)%len(surnames)] + names[(i-1)//len(surnames)%len(names)]
        status = 'ACTIVE' if i <= 900 else ('SUSPENDED' if i <= 930 else
                 ('GRADUATED' if i <= 970 else 'WITHDRAWN'))
        scenario = ('店主' if i <= 30 else '开店申请' if i <= 45 else
                    '订单与购物车' if 101 <= i <= 700 else '普通学生')
        # 末尾十名专门覆盖首次改密，其他账号可直接进入业务页面。
        user(uid, login, 'STUDENT', name, scenario + '；学籍=' + status, i > 990)
        add('tblStudent', studentId=f'bulk-student-{i:04}', userId=uid,
            studentNumber=f'{800+major}26{class_no}{serial:02}', studentType='UNDERGRADUATE',
            studentName=name, gender='男' if i%2 else '女',
            email=f'student{i:04}@example.com', phone=f'139000{i:05}',
            idDocumentType='护照', idDocumentNumber=f'TEST{i:08}',
            birthDate=date(2007, (i-1)%12+1, (i-1)%28+1),
            classId=f'bulk-class-{major:02}-{class_no}', enrollmentDate=date(2026,9,1),
            studentStatus=status, enrolled=status in ('ACTIVE','SUSPENDED'),
            onCampus=status=='ACTIVE', campus='九龙湖校区',
            educationLevel='本科', trainingMode='普通全日制', programLengthYears=4,
            expectedGraduationDate=date(2030,6,30), rowVersion=0, createdAt=now, updatedAt=now)
    return accounts
