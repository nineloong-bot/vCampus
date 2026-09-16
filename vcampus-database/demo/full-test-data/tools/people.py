"""合成账户与学籍：独立密码盐，院系、班级及学号保持对应。"""
import hashlib
import base64
from datetime import date

PASSWORD = 'Test12345'
DEPARTMENTS = ['计算机', '数学', '外国语', '经济管理', '艺术设计', '物理', '生命科学', '法学']
MAJORS = ['软件工程', '计算机科学', '数学应用', '统计学', '英语', '日语',
          '经济学', '管理学', '视觉传达', '产品设计', '物理学', '电子信息科学',
          '生物科学', '生物技术', '法学', '知识产权']
STUDENT_COUNT = 2400
SEED_ACCOUNT_SERIAL_OFFSETS = {2023: 1000, 2024: 3000}


def credentials(key):
    salt = hashlib.sha256(('vcampus-fixture:' + key).encode()).digest()[:16]
    value = hashlib.pbkdf2_hmac('sha256', PASSWORD.encode(), salt, 120000)
    return dict(passwordHash=base64.b64encode(value).decode(),
                passwordSalt=base64.b64encode(salt).decode(), passwordIterations=120000)


def student_name(index):
    """Return the deterministic display name used by the bulk student fixture."""
    surnames = '赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨'
    names = ['明轩', '雨桐', '子涵', '思远', '欣然', '浩宇', '若宁', '文博']
    return surnames[(index - 1) % len(surnames)] + names[(index - 1)//len(surnames) % len(names)]


def generate(add, now):
    accounts = []

    def user(uid, login, role, name, scenario, initial=False):
        add('tblUser', userId=uid, loginId=login, **credentials(uid), roleCode=role,
            accountStatus='ACTIVE', mustChangePassword=initial, failedLoginCount=0,
            lockedUntil=None, lastLoginAt=None, rowVersion=0, createdAt=now, updatedAt=now)
        accounts.append(dict(login=login, password=PASSWORD, role=role, name=name,
                             scenario=scenario, first_change='是' if initial else '否'))

    user('bulk-admin-001', 'TESTADMIN', 'SUPER_ADMIN', '平台管理员', '管理全部校园数据')
    user('bulk-user-admin-002', 'USER_ADMIN_2', 'USER_ADMIN',
         '用户平台主管', '权限调整、交换与停用场景')
    for i in range(1, 51):
        user(f'bulk-teacher-{i:03}', f'TESTTEACHER{i:03}', 'TEACHER', f'任课教师{i:03}',
             '课程管理、图书借阅、普通买家')
    for i, name in enumerate(DEPARTMENTS, 1):
        add('tblDepartment', departmentId=f'bulk-dept-{i:02}', departmentCode=f'TEST{i:02}',
            departmentName=f'{name}学院', isActive=True, rowVersion=0)
    for i, name in enumerate(MAJORS, 1):
        add('tblMajor', majorId=f'bulk-major-{i:02}', departmentId=f'bulk-dept-{(i+1)//2:02}',
            majorCode=str(800+i), majorName=name, isActive=True, rowVersion=0)
        for cohort in range(2023, 2027):
            add('tblClass', classId=f'bulk-class-{i:02}-{cohort}',
                majorId=f'bulk-major-{i:02}', classCode=f'{800+i}-{cohort}-01',
                className=f'{800+i}{cohort % 100:02}1班', enrollmentYear=cohort,
                classNumber=1, isActive=True, rowVersion=0)
    for i in range(1, STUDENT_COUNT + 1):
        major, local = (i-1)//150+1, (i-1)%150
        cohort, serial = 2023 + (i - 1) % 4, local + 1
        login_serial = i + SEED_ACCOUNT_SERIAL_OFFSETS.get(cohort, 0)
        login, uid = f'213{cohort % 100:02d}{login_serial:04}', f'bulk-student-user-{i:04}'
        name = student_name(i)
        status = 'ACTIVE' if i <= 2160 else ('SUSPENDED' if i <= 2220 else
                 ('GRADUATED' if i <= 2320 else 'WITHDRAWN'))
        scenario = ('店主' if i <= 30 else '开店申请' if i <= 45 else
                    '订单与购物车' if 101 <= i <= 700 else '普通学生')
        user(uid, login, 'STUDENT', name, scenario + '；学籍=' + status,
             True)
        add('tblStudent', studentId=f'bulk-student-{i:04}', userId=uid,
            studentNumber=f'{major:02}{cohort % 100:02}1{serial:03}', studentType='UNDERGRADUATE',
            studentName=name, gender='男' if i%2 else '女',
            email=f'student{i:04}@example.com', phone=f'139000{i:05}',
            idDocumentType='护照', idDocumentNumber=f'TEST{i:08}',
            birthDate=date(2007, (i-1)%12+1, (i-1)%28+1),
            classId=f'bulk-class-{major:02}-{cohort}', enrollmentDate=date(cohort,9,1),
            studentStatus=status, enrolled=status in ('ACTIVE','SUSPENDED'),
            onCampus=status=='ACTIVE', campus='九龙湖校区',
            educationLevel='本科', trainingMode='普通全日制', programLengthYears=4,
            expectedGraduationDate=date(cohort+4,6,30), rowVersion=0, createdAt=now, updatedAt=now)
    return accounts


def validate_identity_fixture(rows):
    """Validate that generated student logins agree with class enrollment years."""
    users = {row["userId"]: row for row in rows["tblUser"]}
    classes = {row["classId"]: row for row in rows["tblClass"]}
    seen_logins = set()
    for student in rows["tblStudent"]:
        if not student["studentId"].startswith("bulk-"):
            continue
        user = users[student["userId"]]
        cohort = classes[student["classId"]]["enrollmentYear"]
        expected_prefix = f"213{cohort % 100:02d}"
        if not user["loginId"].startswith(expected_prefix):
            raise AssertionError("student login year disagrees with enrollment year")
        if user["loginId"] in seen_logins:
            raise AssertionError("duplicate student login")
        seen_logins.add(user["loginId"])
