"""课程模块批量演示数据；由总生成器传入 add(table, **fields) 和当前时间。"""
from collections import Counter, defaultdict
from datetime import timedelta
from decimal import Decimal

COURSE_COUNT = 160
COURSES_PER_SEMESTER = 20
PLAN_COURSES_PER_SEMESTER = 5
COURSE_CREDIT = Decimal("3.0")
DEPARTMENTS = ["计算机学院", "数学学院", "外国语学院", "经济管理学院",
               "艺术设计学院", "物理学院", "生命科学学院", "法学学院"]


def generate(add, now):
    stamp = dict(rowVersion=0, createdAt=now, updatedAt=now)
    current, previous = "bulk-term-current", "bulk-term-previous"
    spring = "bulk-term-spring"
    for term, delta, year, season, status, title in (
        (current, 0, 2026, "AUTUMN", "ACTIVE", "2026-2027学年秋季学期"),
        (spring, 165, 2026, "SPRING", "PLANNED", "2026-2027学年春季学期"),
        (previous, -365, 2025, "AUTUMN", "CLOSED", "2025-2026学年秋季学期"),
    ):
        base = now + timedelta(days=delta)
        add("tblTerm", termId=term,
            termCode=f"{year}-{year+1}-{season}",
            termName=title, startDate=(base - timedelta(days=7)).date(),
            endDate=(base + timedelta(days=140)).date(),
            academicYearStart=year, season=season,
            enrollmentStartAt=base - timedelta(days=14),
            enrollmentEndAt=base + timedelta(days=30),
            adjustmentStartAt=base + timedelta(days=31),
            adjustmentEndAt=base + timedelta(days=45), termStatus=status, **stamp)
    # 服务使用手动阶段；全库仅允许一个 OPEN 或 PREVIEW。
    for phase, kind, status, title in (
        ("bulk-phase-enrollment", "ENROLLMENT", "OPEN", "2026-2027学年秋季学期正式选课"),
        ("bulk-phase-adjustment", "ADJUSTMENT", "DRAFT", "2026-2027学年秋季学期退改补选"),
    ):
        add("tblCourseSelectionPhase", phaseId=phase, termId=current,
            phaseType=kind, displayTitle=title, phaseStatus=status, **stamp)
    names = ["程序设计", "数据结构", "数据库原理", "计算机网络", "高等数学",
             "线性代数", "大学英语", "大学物理", "人工智能", "软件工程",
             "专业英语", "离散数学", "概率统计", "操作系统", "编译原理",
             "经济学原理", "管理学基础", "设计基础", "法学概论", "实验方法",
             "科研训练", "创新实践", "学术写作", "社会调查"]
    for course in range(1, COURSE_COUNT + 1):
        department = (course - 1) // COURSES_PER_SEMESTER + 1
        add("tblCourse", courseId=f"bulk-course-{course:03d}",
            courseCode=f"BULK-C{course:03d}",
            courseName=f"{names[(course - 1) % len(names)]}{(course - 1) // len(names) + 1}",
            departmentId=f"bulk-dept-{department:02d}",
            departmentName=DEPARTMENTS[department - 1],
            credit=COURSE_CREDIT,
            totalHours=48,
            description="批量合成课程，用于分页、搜索、选课及教学班管理。",
            isActive=True, **stamp)
    generate_plans(add, now)

    # 正常选课来自学生年级对应的秋季培养方案；前 50 人另保留一门已失败课程作为重修。
    selections = []
    for student in range(1, 901):
        cohort = 2023 + (student - 1) % 4
        year = 2026 - cohort + 1
        major = (student - 1) // 150 + 1
        current_semester = (year - 1) * 2 + 1
        current_courses = plan_courses(major, current_semester)
        retake = plan_courses(major, current_semester - 1)[(student - 1) % 5] \
            if student <= 50 and current_semester > 1 else None
        if retake is not None:
            selections.append((student, retake, (retake - 1) * 2 + 1, True))
            current_courses = [course for course in current_courses
                               if (course - 1) % 20 != (retake - 1) % 20]
        offset = (student - 1) % len(current_courses)
        rotated = current_courses[offset:] + current_courses[:offset]
        for course in rotated[:2 if retake is not None else 3]:
            section = (student - 1) % 2
            selections.append((student, course, (course - 1) * 2 + section + 1, False))
    counts = Counter(o for _, _, o, retake in selections if not retake)
    retake_counts = Counter(o for _, _, o, retake in selections if retake)
    for offering in range(1, COURSE_COUNT * 2 + 1):
        course, section = (offering + 1) // 2, (offering - 1) % 2
        slot = (course - 1) % 20
        add("tblCourseOffering", offeringId=f"bulk-offering-{offering:03d}",
            termId=current, courseId=f"bulk-course-{course:03d}",
            teacherUserId=f"bulk-teacher-{((course - 1) // 20) * 2 + section + 1:03d}",
            className=f"{names[(course - 1) % len(names)]}{(course - 1) // len(names) + 1}"
                      f"-{('A' if offering % 2 else 'B')}班",
            capacity=max(40, counts[offering]),
            enrolledCount=counts[offering],
            offeringStatus="CLOSED" if offering == COURSE_COUNT * 2 else "OPEN", **stamp)
        add("tblCourseSchedule", scheduleId=f"bulk-schedule-{offering:03d}",
            offeringId=f"bulk-offering-{offering:03d}",
            dayOfWeek=slot % 5 + 1,
            startPeriod=1 + (slot // 5) * 2, endPeriod=2 + (slot // 5) * 2,
            startWeek=1, endWeek=18,
            classroom=f"{['教一', '教二', '教三', '计算中心'][slot % 4]}-{101 + offering % 50}")
        add("tblCourseRetakeQuota", offeringId=f"bulk-offering-{offering:03d}",
            capacity=5, enrolledCount=retake_counts[offering])
    generate_season_offerings(add, spring, "spring", range(1, 61), stamp)
    for index, (student, course, offering, retake) in enumerate(selections, 1):
        add("tblEnrollment", enrollmentId=f"bulk-enrollment-{index:04d}",
            offeringId=f"bulk-offering-{offering:03d}", studentId=f"bulk-student-{student:04d}",
            enrollmentType="RETAKE" if retake else "NORMAL", enrollmentStatus="ACTIVE",
            enrolledAt=now - timedelta(days=2), droppedAt=None, **stamp)
    # 退选记录不计入 enrolledCount，保留唯一 student/offering 对。
    for student in range(1, 21):
        add("tblEnrollment", enrollmentId=f"bulk-enrollment-dropped-{student:03d}",
            offeringId="bulk-offering-238", studentId=f"bulk-student-{student:04d}",
            enrollmentType="NORMAL", enrollmentStatus="DROPPED",
            enrolledAt=now - timedelta(days=3), droppedAt=now - timedelta(days=1), **stamp)
    attempts = [(student, course, "FAILED")
                for student, course, _, retake in selections if retake]
    for index, (student, course, outcome) in enumerate(attempts, 1):
        add("tblCourseAttempt", attemptId=f"bulk-attempt-{index:03d}",
            studentId=f"bulk-student-{student:04d}", courseId=f"bulk-course-{course:03d}",
            termId=previous, outcome=outcome, sourceReference=f"bulk-outcome-{index:03d}",
            importedAt=now - timedelta(days=20))
    # 正式选课期尝试补选会被阶段规则拒绝，保留真实规则对应的失败审计。
    for student in range(1, 21):
        add("tblEnrollmentAdjustment", adjustmentId=f"bulk-adjustment-{student:03d}",
            studentId=f"bulk-student-{student:04d}", adjustmentType="LATE_ADD",
            sourceOfferingId=None, targetOfferingId="bulk-offering-150",
            operationResult="FAILED", failureCode="COURSE_ADJUSTMENT_NOT_OPEN",
            operatedAt=now - timedelta(hours=1))


def self_check():
    """独立内存校验：关系、人数、容量、学籍范围及师生时间安排。"""
    from datetime import datetime
    rows = defaultdict(list)
    generate(lambda table, **fields: rows[table].append(fields), datetime(2026, 9, 7, 12))
    offers = {o["offeringId"]: o for o in rows["tblCourseOffering"]}
    schedules = {s["offeringId"]: s for s in rows["tblCourseSchedule"]}
    active = [e for e in rows["tblEnrollment"] if e["enrollmentStatus"] == "ACTIVE"]
    counts, retake_counts = Counter(), Counter()
    selected, student_slots, teacher_slots = set(), set(), set()
    for e in active:
        o, s = offers[e["offeringId"]], schedules[e["offeringId"]]
        assert int(e["studentId"].split("-")[-1]) <= 900
        key = (e["studentId"], o["courseId"])
        assert key not in selected and o["offeringStatus"] == "OPEN"
        selected.add(key)
        slot = (e["studentId"], s["dayOfWeek"], s["startPeriod"])
        assert slot not in student_slots
        student_slots.add(slot)
        target = retake_counts if e["enrollmentType"] == "RETAKE" else counts
        target[o["offeringId"]] += 1
    for o in offers.values():
        assert counts[o["offeringId"]] == o["enrolledCount"] <= o["capacity"]
        s = schedules[o["offeringId"]]
        slot = (o["termId"], o["teacherUserId"], s["dayOfWeek"], s["startPeriod"])
        assert slot not in teacher_slots
        teacher_slots.add(slot)
    quotas = {q["offeringId"]: q for q in rows["tblCourseRetakeQuota"]}
    assert set(quotas) == set(offers)
    for offering, quota in quotas.items():
        assert quota["capacity"] == 5
        assert quota["enrolledCount"] == retake_counts[offering] <= quota["capacity"]
    pairs = {(e["studentId"], e["offeringId"]) for e in rows["tblEnrollment"]}
    assert len(pairs) == len(rows["tblEnrollment"]) == 2720
    outcomes = {(a["studentId"], a["courseId"], a["outcome"]) for a in rows["tblCourseAttempt"]}
    for e in active:
        pair = (e["studentId"], offers[e["offeringId"]]["courseId"])
        assert (*pair, "PASSED") not in outcomes
        assert (e["enrollmentType"] == "RETAKE") == ((*pair, "FAILED") in outcomes)
    assert len(active) == 2700 and len(offers) == 380
    assert sum(p["phaseStatus"] in ("OPEN", "PREVIEW") for p in rows["tblCourseSelectionPhase"]) == 1
    return {table: len(values) for table, values in rows.items()}


def plan_courses(major, semester):
    """Return five catalog courses for one major's semester position."""
    base = (semester - 1) * COURSES_PER_SEMESTER
    major_group = (major - 1) % (COURSES_PER_SEMESTER // PLAN_COURSES_PER_SEMESTER)
    start = base + major_group * PLAN_COURSES_PER_SEMESTER + 1
    return list(range(start, start + PLAN_COURSES_PER_SEMESTER))


def generate_plans(add, now):
    """Emit one canonical four-year, two-season plan per major and cohort."""
    for major in range(1, 17):
        for cohort in range(2023, 2027):
            plan = f"bulk-plan-{major:02}-{cohort}"
            add("tblTrainingPlan", planId=plan, majorId=f"bulk-major-{major:02}",
                enrollmentYear=cohort, planName=f"{800+major}专业{cohort}级培养方案",
                minElectiveCount=8, minElectiveCredits=16, isActive=True,
                rowVersion=1, createdAt=now, updatedAt=now)
            for semester in range(1, 9):
                planned_courses = plan_courses(major, semester)
                for local, course in enumerate(planned_courses):
                    prerequisite = planned_courses[local - 1] if local else None
                    add("tblTrainingPlanCourse", planCourseId=f"{plan}-c{course:03d}-s{semester:02d}",
                        planId=plan, courseCode=f"BULK-C{course:03d}",
                        courseName=f"培养方案课程{course:03d}", credits=COURSE_CREDIT,
                        courseType="ELECTIVE" if local == 4 else "REQUIRED",
                        semester=semester,
                        courseNature="ELECTIVE" if local == 4 else "REQUIRED",
                        courseCategory="专业方向课" if local == 4 else "专业基础课",
                        offeringUnit=DEPARTMENTS[(course - 1) // COURSES_PER_SEMESTER], isActive=True,
                        rowVersion=0, createdAt=now, updatedAt=now)
                    if prerequisite is not None:
                        add("tblTrainingPlanPrerequisite",
                            prerequisiteId=f"{plan}-pre-s{semester:02d}-c{course:03d}", planId=plan,
                            courseId=f"bulk-course-{course:03d}",
                            prerequisiteCourseId=f"bulk-course-{prerequisite:03d}")


def generate_season_offerings(add, term, label, courses, stamp):
    season_name = {"spring": "春季"}[label]
    for course in courses:
        offering = f"bulk-{label}-offering-{course:03d}"
        add("tblCourseOffering", offeringId=offering, termId=term,
            courseId=f"bulk-course-{course:03d}",
            teacherUserId=f"bulk-teacher-{course % 50 + 1:03d}",
            className=f"{season_name}课程{course:03d}-A班", capacity=40,
            enrolledCount=0, offeringStatus="OPEN", **stamp)
        add("tblCourseSchedule", scheduleId=f"bulk-{label}-schedule-{course:03d}",
            offeringId=offering, dayOfWeek=(course - 1) % 5 + 1,
            startPeriod=1 + ((course - 1) // 5 % 4) * 2,
            endPeriod=2 + ((course - 1) // 5 % 4) * 2,
            startWeek=1, endWeek=18,
            classroom=f"{season_name}教学楼-{101 + course % 50}")
        add("tblCourseRetakeQuota", offeringId=offering, capacity=5, enrolledCount=0)


if __name__ == "__main__":
    print(self_check())
