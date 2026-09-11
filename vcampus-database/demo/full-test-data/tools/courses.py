"""课程模块批量演示数据；由总生成器传入 add(table, **fields) 和当前时间。"""
from collections import Counter, defaultdict
from datetime import timedelta
from decimal import Decimal


def generate(add, now):
    stamp = dict(rowVersion=0, createdAt=now, updatedAt=now)
    current, previous = "bulk-term-current", "bulk-term-previous"
    spring, summer = "bulk-term-spring", "bulk-term-summer"
    for term, delta, year, season, status, title in (
        (current, 0, 2026, "AUTUMN", "ACTIVE", "2026-2027学年秋季学期"),
        (spring, 165, 2026, "SPRING", "PLANNED", "2026-2027学年春季学期"),
        (summer, -68, 2026, "SUMMER", "CLOSED", "2026-2027学年暑期学期"),
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
        ("bulk-phase-enrollment", "ENROLLMENT", "OPEN", "批量测试正式选课"),
        ("bulk-phase-adjustment", "ADJUSTMENT", "DRAFT", "批量测试退改补选"),
    ):
        add("tblCourseSelectionPhase", phaseId=phase, termId=current,
            phaseType=kind, displayTitle=title, phaseStatus=status, **stamp)
    names = ["程序设计", "数据结构", "数据库原理", "计算机网络", "高等数学",
             "线性代数", "大学英语", "大学物理", "人工智能", "软件工程"]
    for course in range(1, 121):
        add("tblCourse", courseId=f"bulk-course-{course:03d}",
            courseCode=f"BULK-C{course:03d}",
            courseName=f"{names[(course - 1) % len(names)]}（测试{course:03d}）",
            credit=Decimal("2.0") + Decimal(course % 4),
            totalHours=(2 + course % 4) * 16,
            description="批量合成课程，用于分页、搜索、选课及教学班管理。",
            isActive=True, **stamp)
    generate_plans(add, now)

    # 正常选课来自学生年级对应的秋季培养方案；前 50 人另保留一门已失败课程作为重修。
    selections = []
    for student in range(1, 901):
        cohort = 2023 + ((student - 1) % 100) // 25
        year = 2026 - cohort + 1
        current_courses = list(range((year - 1) * 30 + 11, (year - 1) * 30 + 21))
        current_courses = [course for course in current_courses if course not in range(2, 121, 15)]
        retake = (student - 1) % 40 + 1 if student <= 50 else None
        if retake is not None:
            selections.append((student, retake, (retake - 1) * 2 + 1, True))
            current_courses = [course for course in current_courses
                               if (course - 1) % 20 != (retake - 1) % 20]
        offset = (student - 1) % len(current_courses)
        rotated = current_courses[offset:] + current_courses[:offset]
        for course in rotated[:2 if retake is not None else 3]:
            section = (student - 1) % 2
            selections.append((student, course, (course - 1) * 2 + section + 1, False))
    counts = Counter(o for _, _, o, _ in selections)
    for offering in range(1, 241):
        course, section = (offering + 1) // 2, (offering - 1) % 2
        slot = (course - 1) % 20
        add("tblCourseOffering", offeringId=f"bulk-offering-{offering:03d}",
            termId=current, courseId=f"bulk-course-{course:03d}",
            teacherUserId=f"bulk-teacher-{((course - 1) // 20) * 2 + section + 1:03d}",
            className=f"测试课程{course:03d}-{('A' if offering % 2 else 'B')}班",
            capacity=counts[offering] if offering == 80 else 40,
            enrolledCount=counts[offering],
            offeringStatus="CLOSED" if offering == 240 else "OPEN", **stamp)
        add("tblCourseSchedule", scheduleId=f"bulk-schedule-{offering:03d}",
            offeringId=f"bulk-offering-{offering:03d}",
            dayOfWeek=slot % 5 + 1,
            startPeriod=1 + (slot // 5) * 2, endPeriod=2 + (slot // 5) * 2,
            startWeek=1, endWeek=18, classroom=f"测试教学楼-{offering:03d}")
    generate_season_offerings(add, spring, "spring", range(1, 31), stamp)
    generate_season_offerings(add, summer, "summer", range(31, 51), stamp)
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
    attempts = [(s, (s - 1) % 40 + 1, "FAILED") for s in range(1, 51)]
    attempts += [(s, 75, "FAILED") for s in range(1, 26)]
    attempts += [(s, 76, "PASSED") for s in range(1, 26)]
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
    counts, selected, student_slots, teacher_slots = Counter(), set(), set(), set()
    for e in active:
        o, s = offers[e["offeringId"]], schedules[e["offeringId"]]
        assert int(e["studentId"].split("-")[-1]) <= 900
        key = (e["studentId"], o["courseId"])
        assert key not in selected and o["offeringStatus"] == "OPEN"
        selected.add(key)
        slot = (e["studentId"], s["dayOfWeek"], s["startPeriod"])
        assert slot not in student_slots
        student_slots.add(slot)
        counts[o["offeringId"]] += 1
    for o in offers.values():
        assert counts[o["offeringId"]] == o["enrolledCount"] <= o["capacity"]
        s = schedules[o["offeringId"]]
        slot = (o["termId"], o["teacherUserId"], s["dayOfWeek"], s["startPeriod"])
        assert slot not in teacher_slots
        teacher_slots.add(slot)
    pairs = {(e["studentId"], e["offeringId"]) for e in rows["tblEnrollment"]}
    assert len(pairs) == len(rows["tblEnrollment"]) == 2720
    outcomes = {(a["studentId"], a["courseId"], a["outcome"]) for a in rows["tblCourseAttempt"]}
    for e in active:
        pair = (e["studentId"], offers[e["offeringId"]]["courseId"])
        assert (*pair, "PASSED") not in outcomes
        assert (e["enrollmentType"] == "RETAKE") == ((*pair, "FAILED") in outcomes)
    assert len(active) == 2700 and len(offers) == 290
    assert sum(p["phaseStatus"] in ("OPEN", "PREVIEW") for p in rows["tblCourseSelectionPhase"]) == 1
    return {table: len(values) for table, values in rows.items()}


def generate_plans(add, now):
    """Emit one canonical four-year, three-season plan per major and cohort."""
    for major in range(1, 11):
        for cohort in range(2023, 2027):
            plan = f"bulk-plan-{major:02}-{cohort}"
            add("tblTrainingPlan", planId=plan, majorId=f"bulk-major-{major:02}",
                enrollmentYear=cohort, planName=f"{800+major}专业{cohort}级培养方案",
                minElectiveCount=8, minElectiveCredits=16, isActive=True,
                rowVersion=1, createdAt=now, updatedAt=now)
            for course in range(1, 121):
                year, local = (course - 1) // 30 + 1, (course - 1) % 30
                season_ordinal = local // 10 + 1
                add("tblTrainingPlanCourse", planCourseId=f"{plan}-c{course:03d}",
                    planId=plan, courseCode=f"BULK-C{course:03d}",
                    courseName=f"培养方案课程{course:03d}", credits=2 + course % 4,
                    courseType="ELECTIVE" if course % 4 == 0 else "REQUIRED",
                    semester=(year - 1) * 3 + season_ordinal,
                    courseNature="ELECTIVE" if course % 4 == 0 else "REQUIRED",
                    courseCategory="专业方向课" if course % 4 == 0 else "专业基础课",
                    offeringUnit=f"{800+major}专业所在学院", isActive=True,
                    rowVersion=0, createdAt=now, updatedAt=now)
            for course in range(2, 121, 15):
                add("tblTrainingPlanPrerequisite",
                    prerequisiteId=f"{plan}-pre-{course:03d}", planId=plan,
                    courseId=f"bulk-course-{course:03d}",
                    prerequisiteCourseId=f"bulk-course-{course-1:03d}")


def generate_season_offerings(add, term, label, courses, stamp):
    for course in courses:
        offering = f"bulk-{label}-offering-{course:03d}"
        add("tblCourseOffering", offeringId=offering, termId=term,
            courseId=f"bulk-course-{course:03d}",
            teacherUserId=f"bulk-teacher-{course % 50 + 1:03d}",
            className=f"{label}课程{course:03d}-A班", capacity=40,
            enrolledCount=0, offeringStatus="OPEN", **stamp)
        add("tblCourseSchedule", scheduleId=f"bulk-{label}-schedule-{course:03d}",
            offeringId=offering, dayOfWeek=(course - 1) % 5 + 1,
            startPeriod=1 + ((course - 1) // 5 % 4) * 2,
            endPeriod=2 + ((course - 1) // 5 % 4) * 2,
            startWeek=1, endWeek=6 if label == "summer" else 18,
            classroom=f"{label}教学楼-{course:03d}")


if __name__ == "__main__":
    print(self_check())
