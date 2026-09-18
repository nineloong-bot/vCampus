"""Realistic curricula, grades, and an initially empty selection term."""
from collections import Counter, defaultdict
from datetime import date, timedelta
from decimal import Decimal

import people

TRANSFER_APPLICANTS = {
    "student-2025-025", "student-2025-026", "student-2026-025",
    "student-2026-026", "student-2026-027",
}

COMMON = (
    (("SEU101", "思想道德与法治", "3.0"), ("SEU102", "大学英语Ⅰ", "2.0"), ("SEU103", "体育Ⅰ", "0.5")),
    (("SEU104", "中国近现代史纲要", "3.0"), ("SEU105", "大学英语Ⅱ", "2.0"), ("SEU106", "体育Ⅱ", "0.5")),
    (("SEU201", "马克思主义基本原理", "3.0"), ("SEU202", "大学物理Ⅰ", "3.5"), ("SEU203", "体育Ⅲ", "0.5")),
    (("SEU204", "毛泽东思想和中国特色社会主义理论体系概论", "3.0"), ("SEU205", "大学物理Ⅱ", "3.5"), ("SEU206", "体育Ⅳ", "0.5")),
    (("SEU301", "形势与政策", "1.0"), ("SEU302", "工程伦理", "1.5"), ("SEU303", "创新创业基础", "1.0")),
    (("SEU304", "学术写作", "1.5"), ("SEU305", "社会实践", "2.0"), ("SEU306", "劳动教育实践", "1.0")),
    (("SEU401", "专业实习", "3.0"), ("SEU402", "科研训练", "2.0"), ("SEU403", "跨学科前沿", "2.0")),
    (("SEU404", "毕业设计", "12.0"), ("SEU405", "毕业教育", "1.0"), ("SEU406", "职业发展", "1.0")),
)
SPECIALTY = {
    "major-cs": (
        ("CS101", "工科数学分析Ⅰ", "5.0"), ("CS102", "程序设计基础及语言", "4.0"),
        ("CS103", "工科数学分析Ⅱ", "5.0"), ("CS104", "离散数学", "3.0"),
        ("CS201", "数据结构基础", "4.0"), ("CS202", "数字逻辑与计算机组成", "3.5"),
        ("CS203", "算法设计与分析", "3.0"), ("CS204", "计算机组成原理", "4.0"),
        ("CS301", "操作系统", "3.5"), ("CS302", "数据库原理", "3.0"),
        ("CS303", "计算机网络", "3.5"), ("CS304", "编译原理", "3.0"),
        ("CS401", "计算机系统综合设计", "3.0"), ("CS402", "网络空间安全基础", "2.0"),
        ("CS403", "高性能计算", "2.0"), ("CS404", "计算机前沿专题", "1.5"),
    ),
    "major-se": (
        ("SE101", "工科数学分析Ⅰ", "5.0"), ("SE102", "程序设计基础", "4.0"),
        ("SE103", "工科数学分析Ⅱ", "5.0"), ("SE104", "面向对象程序设计", "3.0"),
        ("SE201", "数据结构与算法", "4.0"), ("SE202", "软件工程导论", "3.0"),
        ("SE203", "数据库系统", "3.5"), ("SE204", "软件需求工程", "2.5"),
        ("SE301", "软件体系结构", "3.0"), ("SE302", "软件质量保障与验证", "2.5"),
        ("SE303", "操作系统", "3.5"), ("SE304", "计算机网络", "3.0"),
        ("SE401", "软件项目管理", "2.0"), ("SE402", "软件工程综合实践", "4.0"),
        ("SE403", "云计算与服务工程", "2.0"), ("SE404", "软件技术前沿", "1.5"),
    ),
    "major-ai": (
        ("AI101", "工科数学分析Ⅰ", "5.0"), ("AI102", "程序设计基础及语言", "4.0"),
        ("AI103", "工科数学分析Ⅱ", "5.0"), ("AI104", "几何与代数", "3.5"),
        ("AI201", "数据结构基础", "4.0"), ("AI202", "人工智能导论", "2.0"),
        ("AI203", "概率论与随机过程", "3.5"), ("AI204", "知识表示与推理", "3.0"),
        ("AI301", "机器学习", "3.5"), ("AI302", "模式识别", "3.0"),
        ("AI303", "计算机视觉", "3.0"), ("AI304", "自然语言处理", "3.0"),
        ("AI401", "深度学习课程设计", "3.0"), ("AI402", "强化学习", "2.5"),
        ("AI403", "多智能体系统", "2.0"), ("AI404", "人工智能前沿", "1.5"),
    ),
    "major-math": (
        ("MA101", "数学分析Ⅰ", "5.0"), ("MA102", "高等代数与解析几何Ⅰ", "5.0"),
        ("MA103", "数学分析Ⅱ", "5.0"), ("MA104", "高等代数与解析几何Ⅱ", "5.0"),
        ("MA201", "数学分析Ⅲ", "4.0"), ("MA202", "常微分方程", "3.0"),
        ("MA203", "概率论", "3.0"), ("MA204", "复变函数", "3.0"),
        ("MA301", "实变函数", "3.0"), ("MA302", "数理统计", "3.0"),
        ("MA303", "近世代数", "3.0"), ("MA304", "数值分析", "3.0"),
        ("MA401", "拓扑学", "2.5"), ("MA402", "泛函分析", "2.5"),
        ("MA403", "数学物理方程", "2.5"), ("MA404", "现代数学选讲", "2.0"),
    ),
    "major-ics": (
        ("IC101", "数学分析Ⅰ", "5.0"), ("IC102", "高等代数与解析几何Ⅰ", "5.0"),
        ("IC103", "数学分析Ⅱ", "5.0"), ("IC104", "程序设计基础", "4.0"),
        ("IC201", "离散数学", "3.0"), ("IC202", "数据结构与算法", "4.0"),
        ("IC203", "概率论与数理统计", "3.5"), ("IC204", "数值分析", "3.0"),
        ("IC301", "运筹学", "3.0"), ("IC302", "数据库原理", "3.0"),
        ("IC303", "最优化方法", "3.0"), ("IC304", "数学建模与数学实验", "3.0"),
        ("IC401", "机器学习基础", "2.5"), ("IC402", "时间序列分析", "2.5"),
        ("IC403", "复杂网络与人工智能", "2.0"), ("IC404", "科学计算前沿", "1.5"),
    ),
}


def _definitions():
    rows = {}
    for semester, common in enumerate(COMMON, 1):
        for code, name, credit in common:
            rows[code] = (name, Decimal(credit), semester, "dept-math" if "数学" in name else "dept-cse")
    for major_id, courses in SPECIALTY.items():
        department = "dept-math" if major_id in {"major-math", "major-ics"} else "dept-cse"
        for index, (code, name, credit) in enumerate(courses):
            rows[code] = (name, Decimal(credit), index // 2 + 1, department)
    return rows


def _course_slot(code):
    """Return a stable non-conflicting slot for a curriculum course."""
    for semester, common in enumerate(COMMON, 1):
        for position, course in enumerate(common):
            if course[0] == code:
                return (semester - 1) * 5 + position
    for specialty in SPECIALTY.values():
        for index, course in enumerate(specialty):
            if course[0] == code:
                return (index // 2) * 5 + 3 + index % 2
    raise ValueError(f"unknown course code: {code}")


def generate(add, now):
    """Generate curricula, historical grades, and one open selection term."""
    stamp = dict(rowVersion=0, createdAt=now, updatedAt=now)
    term_id = "term-2026-autumn"
    add("tblTerm", termId=term_id, termCode="2026-2027-AUTUMN",
        termName="2026-2027学年秋季学期", startDate=date(2026, 10, 12),
        endDate=date(2027, 2, 19), academicYearStart=2026, season="AUTUMN",
        enrollmentStartAt=now - timedelta(days=1), enrollmentEndAt=now + timedelta(days=14),
        adjustmentStartAt=now + timedelta(days=15), adjustmentEndAt=now + timedelta(days=21),
        termStatus="ACTIVE", **stamp)
    add("tblCourseSelectionPhase", phaseId="phase-2026-autumn", termId=term_id,
        phaseType="ENROLLMENT", displayTitle="2026-2027学年秋季学期正式选课",
        phaseStatus="OPEN", **stamp)

    departments = {row[0]: row[2] for row in people.DEPARTMENTS}
    definitions = _definitions()
    ordinary_rooms = []
    add("tblClassroom", classroom="纪忠楼-空闲小教室", capacity=30,
        isActive=True, sharedSportsVenue=False)
    for tier, count in ((50, 8), (100, 8), (200, 8)):
        for number in range(1, count + 1):
            room = f"纪忠楼-{tier}-{number:02d}"
            ordinary_rooms.append(room)
            add("tblClassroom", classroom=room, capacity=tier,
                isActive=True, sharedSportsVenue=False)
    add("tblClassroom", classroom="桃园操场", capacity=2147483647,
        isActive=True, sharedSportsVenue=True)
    for code, (name, credit, semester, department_id) in definitions.items():
        add("tblCourse", courseId=f"course-{code.lower()}", courseCode=code,
            courseName=name, departmentId=department_id, departmentName=departments[department_id],
            credit=credit, totalHours=max(16, int(credit * 16)),
            description=f"{name}课程，包含理论学习与实践训练。", isActive=True, **stamp)

    plan_rows = {}
    for major_id, _, _, major_name in people.MAJORS:
        specialty = SPECIALTY[major_id]
        for cohort in people.COHORTS:
            plan_id = f"plan-{major_id[6:]}-{cohort}"
            add("tblTrainingPlan", planId=plan_id, majorId=major_id, enrollmentYear=cohort,
                planName=f"{major_name}{cohort}级本科培养方案", minElectiveCount=4,
                minElectiveCredits=Decimal("8.0"), isActive=True, **stamp)
            plan_rows[(major_id, cohort)] = []
            for semester in range(1, 9):
                selected = list(COMMON[semester - 1]) + list(specialty[(semester - 1) * 2:semester * 2])
                for position, (code, name, credit_text) in enumerate(selected, 1):
                    credit = Decimal(credit_text)
                    nature = "REQUIRED" if position <= 4 else (
                        "CROSS_DISCIPLINARY" if semester >= 7 else "ELECTIVE")
                    plan_course_id = f"pc-{major_id[6:]}-{cohort}-{code.lower()}"
                    department_id = definitions[code][3]
                    add("tblTrainingPlanCourse", planCourseId=plan_course_id, planId=plan_id,
                        courseCode=code, courseName=name, credits=credit,
                        totalHours=max(16, int(credit * 16)), courseType=nature,
                        semester=semester, courseNature=nature,
                        courseCategory=_category(semester, position),
                        offeringUnit=departments[department_id], courseId=f"course-{code.lower()}",
                        offeringDepartmentId=department_id,
                        offeringDepartmentName=departments[department_id], allocatedQuota=40,
                        isActive=True, **stamp)
                    plan_rows[(major_id, cohort)].append((plan_course_id, semester, nature, code))
            for semester in range(2, 9):
                course = specialty[(semester - 1) * 2][0]
                prerequisite = specialty[(semester - 2) * 2][0]
                add("tblTrainingPlanPrerequisite",
                    prerequisiteId=f"pre-{major_id[6:]}-{cohort}-{semester}", planId=plan_id,
                    courseId=f"course-{course.lower()}",
                    prerequisiteCourseId=f"course-{prerequisite.lower()}")

    student_index = 0
    for cohort in people.COHORTS:
        completed = 4 if cohort == 2024 else 2 if cohort == 2025 else 0
        for major_id, _, _, _ in people.MAJORS:
            required = [row for row in plan_rows[(major_id, cohort)]
                        if row[1] <= completed and row[2] == "REQUIRED"]
            for local in range(1, 9):
                student_index += 1
                cohort_serial = (list(m[0] for m in people.MAJORS).index(major_id)) * 8 + local
                for grade_no, (plan_course_id, semester, _, _) in enumerate(required, 1):
                    student_id = f"student-{cohort}-{cohort_serial:03d}"
                    score_passed = (student_index + grade_no) % 17 != 0 \
                        or student_id in TRANSFER_APPLICANTS
                    add("tblStudentGrade", gradeId=f"grade-{cohort}-{cohort_serial:03d}-{grade_no:02d}",
                        studentId=student_id,
                        planCourseId=plan_course_id, result="PASSED" if score_passed else "FAILED",
                        recordedSemester=f"{cohort + (semester - 1) // 2}-{semester}",
                        operatorUserId="user-teacher-01", rowVersion=0,
                        createdAt=now, updatedAt=now)

    slot_occupancy = defaultdict(int)
    for code in definitions:
        slot = _course_slot(code)
        day = slot % 7 + 1
        start_period = 1 + (slot // 7) * 2
        for section in (1, 2):
            offering_id = f"offering-{code.lower()}-{section}"
            teacher_number = slot_occupancy[slot] + 1
            slot_occupancy[slot] += 1
            room = "桃园操场" if definitions[code][0].startswith("体育") \
                else ordinary_rooms[teacher_number - 1]
            add("tblCourseOffering", offeringId=offering_id, termId=term_id,
                courseId=f"course-{code.lower()}",
                teacherUserId=f"user-teacher-{teacher_number:02d}",
                className=f"{definitions[code][0]}-{section:02d}班", capacity=35 + section * 5,
                enrolledCount=0, offeringStatus="OPEN", **stamp)
            add("tblCourseSchedule", scheduleId=f"schedule-{code.lower()}-{section}",
                offeringId=offering_id, dayOfWeek=day,
                startPeriod=start_period, endPeriod=start_period + 1,
                startWeek=1, endWeek=16,
                classroom=room)
            add("tblCourseRetakeQuota", offeringId=offering_id, capacity=5, enrolledCount=0)


def _category(semester, position):
    if semester == 8:
        return "毕业环节"
    if position <= 3:
        return "通识教育基础课"
    if semester <= 2:
        return "学科基础课"
    if semester <= 6:
        return "专业核心课"
    return "专业选修与交叉课程"


def validate_course_fixture(rows):
    """Validate academic graph invariants before SQL is written."""
    if rows["tblEnrollment"] or rows["tblEnrollmentAdjustment"]:
        raise AssertionError("initial selection records must be empty")
    if sum(row["phaseStatus"] == "OPEN" for row in rows["tblCourseSelectionPhase"]) != 1:
        raise AssertionError("exactly one selection phase must be open")
    offering_counts = Counter(row["courseId"] for row in rows["tblCourseOffering"])
    if not offering_counts or set(offering_counts.values()) != {2}:
        raise AssertionError("every selectable course must have two offerings")
    schedules = Counter(row["offeringId"] for row in rows["tblCourseSchedule"])
    if set(schedules.values()) != {1}:
        raise AssertionError("every offering must have one schedule")
    semesters = defaultdict(set)
    for row in rows["tblTrainingPlanCourse"]:
        semesters[row["planId"]].add(row["semester"])
    if any(values != set(range(1, 9)) for values in semesters.values()):
        raise AssertionError("every plan must cover eight semesters")


def self_check():
    rows = defaultdict(list)
    generate(lambda table, **fields: rows[table].append(fields),
             __import__("datetime").datetime(2026, 9, 16, 12))
    validate_course_fixture(rows)
    return {table: len(values) for table, values in rows.items()}
