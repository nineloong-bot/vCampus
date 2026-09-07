"""课程模块批量演示数据；由总生成器传入 add(table, **fields) 和当前时间。"""
from collections import Counter, defaultdict
from datetime import timedelta
from decimal import Decimal


def generate(add, now):
    stamp = dict(rowVersion=0, createdAt=now, updatedAt=now)
    current, previous = "bulk-term-current", "bulk-term-previous"
    # termCode 降序决定当前学期；BULK 前缀排在原有数字学期之后。
    for term, delta, status, title in (
        (current, 0, "ACTIVE", "批量测试当前学期"),
        (previous, -365, "CLOSED", "批量测试历史学期"),
    ):
        base = now + timedelta(days=delta)
        add("tblTerm", termId=term,
            termCode=("BULK-CURRENT-" if delta == 0 else "BULK-HISTORY-") + now.strftime("%Y%m%d"),
            termName=title, startDate=(base - timedelta(days=7)).date(),
            endDate=(base + timedelta(days=140)).date(),
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

    # 三个课程组分配到周一、二、三；每名学生各选一门，避免课表冲突。
    selections = []
    for student in range(1, 901):
        group, section = (student - 1) % 40, ((student - 1) // 40) % 2
        for day in range(3):
            course = day * 40 + group + 1
            offering = (course - 1) * 2 + section + 1
            if offering == 240:
                offering = 239  # 最后一个教学班关闭，学生归入同课程 A 班。
            selections.append((student, course, offering, day == 0 and student <= 50))
    counts = Counter(o for _, _, o, _ in selections)
    for offering in range(1, 241):
        course, local = (offering + 1) // 2, (offering - 1) % 80
        add("tblCourseOffering", offeringId=f"bulk-offering-{offering:03d}",
            termId=current, courseId=f"bulk-course-{course:03d}",
            teacherUserId=f"bulk-teacher-{local % 50 + 1:03d}",
            className=f"测试课程{course:03d}-{('A' if offering % 2 else 'B')}班",
            capacity=counts[offering] if offering == 80 else 40,
            enrolledCount=counts[offering],
            offeringStatus="CLOSED" if offering == 240 else "OPEN", **stamp)
        add("tblCourseSchedule", scheduleId=f"bulk-schedule-{offering:03d}",
            offeringId=f"bulk-offering-{offering:03d}",
            dayOfWeek=(offering - 1) // 80 + 1,
            startPeriod=1 + (local // 50) * 2, endPeriod=2 + (local // 50) * 2,
            startWeek=1, endWeek=18, classroom=f"测试教学楼-{offering:03d}")
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
        slot = (o["teacherUserId"], s["dayOfWeek"], s["startPeriod"])
        assert slot not in teacher_slots
        teacher_slots.add(slot)
    pairs = {(e["studentId"], e["offeringId"]) for e in rows["tblEnrollment"]}
    assert len(pairs) == len(rows["tblEnrollment"]) == 2720
    outcomes = {(a["studentId"], a["courseId"], a["outcome"]) for a in rows["tblCourseAttempt"]}
    for e in active:
        pair = (e["studentId"], offers[e["offeringId"]]["courseId"])
        assert (*pair, "PASSED") not in outcomes
        assert (e["enrollmentType"] == "RETAKE") == ((*pair, "FAILED") in outcomes)
    assert len(active) == 2700 and len(offers) == 240
    assert sum(p["phaseStatus"] in ("OPEN", "PREVIEW") for p in rows["tblCourseSelectionPhase"]) == 1
    return {table: len(values) for table, values in rows.items()}


if __name__ == "__main__":
    print(self_check())
