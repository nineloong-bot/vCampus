"""Deterministic people and organization data for the release database."""
import base64
import hashlib
from datetime import date

PASSWORD = "123456"
COHORTS = (2024, 2025, 2026)
DEPARTMENTS = (
    ("dept-cse", "CSE", "计算机科学与工程学院"),
    ("dept-math", "MATH", "数学学院"),
)
MAJORS = (
    ("major-cs", "dept-cse", "801", "计算机科学与技术"),
    ("major-se", "dept-cse", "802", "软件工程"),
    ("major-ai", "dept-cse", "803", "人工智能"),
    ("major-math", "dept-math", "701", "数学与应用数学"),
    ("major-ics", "dept-math", "702", "信息与计算科学"),
)
ADMINISTRATORS = (
    ("user-admin", "ADMIN", "SUPER_ADMIN", "平台超级管理员"),
    ("user-student-admin", "STUDENT", "STUDENT_ADMIN", "学籍管理员"),
    ("user-course-admin", "COURSE", "COURSE_ADMIN", "选课管理员"),
    ("user-library-admin", "LIBRARY", "LIBRARY_ADMIN", "图书管理员"),
    ("user-shop-admin", "SHOP", "SHOP_ADMIN", "商城管理员"),
    ("user-account-admin", "USER", "USER_ADMIN", "用户管理员"),
    ("user-cse-admin", "CSADMIN", "COLLEGE_ADMIN", "计算机学院管理员"),
    ("user-math-admin", "MATHADMIN", "COLLEGE_ADMIN", "数学学院管理员"),
)
SURNAMES = "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜"
GIVEN_NAMES = (
    "明轩", "雨桐", "子涵", "思远", "欣然", "浩宇", "若宁", "文博",
    "嘉怡", "宇辰", "诗涵", "俊杰", "佳宁", "梓萱", "承泽", "清妍",
)
PINYIN = {
    "赵": "ZHAO", "钱": "QIAN", "孙": "SUN", "李": "LI", "周": "ZHOU",
    "吴": "WU", "郑": "ZHENG", "王": "WANG", "冯": "FENG", "陈": "CHEN",
    "褚": "CHU", "卫": "WEI", "蒋": "JIANG", "沈": "SHEN", "韩": "HAN",
    "杨": "YANG", "朱": "ZHU", "秦": "QIN", "尤": "YOU", "许": "XU",
    "何": "HE", "吕": "LV", "施": "SHI", "张": "ZHANG", "孔": "KONG",
    "曹": "CAO", "严": "YAN", "华": "HUA", "金": "JIN", "魏": "WEI",
    "陶": "TAO", "姜": "JIANG",
}


def credentials(key):
    """Return deterministic PBKDF2 credentials for the common initial password."""
    salt = hashlib.sha256(("vcampus-release:" + key).encode()).digest()[:16]
    value = hashlib.pbkdf2_hmac("sha256", PASSWORD.encode(), salt, 120000)
    return dict(passwordHash=base64.b64encode(value).decode(),
                passwordSalt=base64.b64encode(salt).decode(), passwordIterations=120000)


def resident_id(birth_date, sequence):
    """Build a checksum-valid Nanjing resident identity number."""
    body = f"320115{birth_date:%Y%m%d}{sequence:03d}"
    weights = (7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    checks = "10X98765432"
    return body + checks[sum(int(n) * w for n, w in zip(body, weights)) % 11]


def valid_resident_id(value):
    """Return whether a resident identity number has a valid checksum."""
    if len(value) != 18 or not value[:17].isdigit():
        return False
    weights = (7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
    checks = "10X98765432"
    return value[-1] == checks[sum(int(n) * w for n, w in zip(value[:17], weights)) % 11]


def _name(index):
    surname = SURNAMES[index % len(SURNAMES)]
    return surname + GIVEN_NAMES[(index // len(SURNAMES)) % len(GIVEN_NAMES)], PINYIN[surname]


def generate(add, now):
    """Generate organizations, staff accounts, classes, and 120 complete profiles."""
    accounts = []

    def user(user_id, login, role, display, first_change=False, scenario="日常使用"):
        add("tblUser", userId=user_id, loginId=login, **credentials(user_id), roleCode=role,
            accountStatus="ACTIVE", mustChangePassword=first_change, failedLoginCount=0,
            lockedUntil=None, lastLoginAt=None, rowVersion=0, createdAt=now, updatedAt=now)
        accounts.append(dict(login=login, password=PASSWORD, role=role, name=display,
                             scenario=scenario, first_change="是" if first_change else "否"))

    for values in ADMINISTRATORS:
        user(*values)
    for number in range(1, 25):
        user(f"user-teacher-{number:02d}", f"T{number:03d}", "TEACHER",
             f"教师{number:02d}", scenario="课程教学")
    for department in DEPARTMENTS:
        add("tblDepartment", departmentId=department[0], departmentCode=department[1],
            departmentName=department[2], isActive=True, rowVersion=0)
    for major_id, department_id, major_code, major_name in MAJORS:
        add("tblMajor", majorId=major_id, departmentId=department_id, majorCode=major_code,
            majorName=major_name, grades="1,2,3,4", isActive=True, rowVersion=0)
        for cohort in COHORTS:
            add("tblClass", classId=f"class-{major_id[6:]}-{cohort}", majorId=major_id,
                classCode=f"{major_code}-{cohort}-01", className=f"{major_name}{str(cohort)[2:]}01班",
                enrollmentYear=cohort, classNumber=1, isActive=True, rowVersion=0)
    add("tblStudentCollegeAdministrator", departmentId="dept-cse", userId="user-cse-admin",
        isActive=True, rowVersion=0, createdAt=now, updatedAt=now)
    add("tblStudentCollegeAdministrator", departmentId="dept-math", userId="user-math-admin",
        isActive=True, rowVersion=0, createdAt=now, updatedAt=now)

    global_index = 0
    for cohort in COHORTS:
        for major_index, (major_id, department_id, major_code, major_name) in enumerate(MAJORS, 1):
            for local in range(1, 9):
                global_index += 1
                cohort_serial = (major_index - 1) * 8 + local
                login = f"213{cohort % 100:02d}{cohort_serial:04d}"
                user_id = f"user-student-{cohort}-{cohort_serial:03d}"
                student_id = f"student-{cohort}-{cohort_serial:03d}"
                name, surname_pinyin = _name(global_index - 1)
                gender = "男" if global_index % 2 else "女"
                month = (cohort_serial - 1) % 8 + 1
                day = (cohort_serial * 3 - 1) % 27 + 1
                birth = date(cohort - 2018, month, day)
                sequence = cohort_serial * 2 + (1 if gender == "男" else 0)
                student_number = f"{major_code}{cohort % 100:02d}1{local:02d}"
                user(user_id, login, "STUDENT", name, True,
                     f"{major_name}{str(cohort)[2:]}级学生")
                add("tblStudent", studentId=student_id, userId=user_id,
                    studentNumber=student_number, studentType="UNDERGRADUATE",
                    studentName=name, gender=gender,
                    email=f"{login}@seu.edu.cn", phone=f"138{cohort % 100:02d}{cohort_serial:06d}",
                    namePinyin=f"{surname_pinyin} XUESHENG", formerName=None,
                    politicalStatus="共青团员", ethnicity="汉族", maritalStatus="未婚",
                    idDocumentType="居民身份证", idDocumentNumber=resident_id(birth, sequence),
                    idIssuedDate=date(birth.year + 16, birth.month, birth.day), birthDate=birth,
                    nativePlace="江苏省南京市", countryRegion="中国", birthplace="江苏省南京市",
                    studentOriginPlace="江苏省南京市", householdRegistrationType="家庭户口",
                    householdBeforeEnrollment="江苏省南京市江宁区",
                    householdAfterEnrollment="江苏省南京市江宁区东南大学九龙湖校区",
                    overseasChineseStatus="否", religion="无宗教信仰", leagueMember=True,
                    leagueJoinDate=date(birth.year + 14, 5, 4), partyMember=False,
                    partyJoinDate=None, healthStatus="健康", bloodType=("A", "B", "O", "AB")[global_index % 4],
                    weightKg=48 + global_index % 28, heightCm=158 + global_index % 27,
                    specialties=("程序设计", "数学建模", "羽毛球", "摄影")[global_index % 4],
                    hobbies=("阅读", "跑步", "音乐", "乒乓球")[global_index % 4],
                    onlyChild=global_index % 3 != 0,
                    classId=f"class-{major_id[6:]}-{cohort}", enrollmentDate=date(cohort, 9, 1),
                    studentStatus="ACTIVE", enrolled=True, onCampus=True, campus="九龙湖校区",
                    educationLevel="本科", trainingMode="普通全日制", programLengthYears=4,
                    attendanceMode="RESIDENT", degreeName="工学学士" if department_id == "dept-cse" else "理学学士",
                    educationName="普通高等教育", expectedGraduationDate=date(cohort + 4, 6, 30),
                    graduationDate=None, studentSource="普通高考", graduateStudyMode=None,
                    counselorName="周宁" if department_id == "dept-cse" else "林悦",
                    counselorContact="025-52090001" if department_id == "dept-cse" else "025-52090002",
                    rowVersion=0, createdAt=now, updatedAt=now)
    for _, _, major_code, _ in MAJORS:
        for cohort in COHORTS:
            add("tblNumberSequence",
                sequenceKey=f"STUDENT_NUMBER:{major_code}:{cohort % 100:02d}:1",
                currentValue=8, maxValue=99, rowVersion=0, updatedAt=now)
    add("tblNumberSequence", sequenceKey="CAMPUS_CARD_GLOBAL",
        currentValue=40, maxValue=9999, rowVersion=0, updatedAt=now)
    return accounts


def validate_identity_fixture(rows):
    """Validate student account, class year, and identity relationships."""
    users = {row["userId"]: row for row in rows["tblUser"]}
    classes = {row["classId"]: row for row in rows["tblClass"]}
    for student in rows["tblStudent"]:
        cohort = classes[student["classId"]]["enrollmentYear"]
        if not users[student["userId"]]["loginId"].startswith(f"213{cohort % 100:02d}"):
            raise AssertionError("student login year disagrees with enrollment year")
        if not valid_resident_id(student["idDocumentNumber"]):
            raise AssertionError("student identity number checksum is invalid")
