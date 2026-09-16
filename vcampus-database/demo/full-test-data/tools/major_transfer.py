"""Generate a deterministic, valid computer-to-mathematics transfer scenario."""
from datetime import date, datetime
from math import floor

import people

BATCH_ID = "bulk-transfer-batch-2026"
OPTION_ID = "bulk-transfer-option-math"
REVERSE_BATCH_ID = "bulk-transfer-batch-reverse-2026"
REVERSE_OPTION_ID = "bulk-transfer-option-reverse"
TRANSFER_COUNT = 240


def generate(add, now: datetime, transfer_students: int = TRANSFER_COUNT):
    """Emit dedicated first-year students, a batch, and submitted applications."""
    if transfer_students < 1:
        raise ValueError("transfer_students must be positive")
    accounts = []
    class_count = (transfer_students + 24) // 25
    for class_no in range(1, class_count + 1):
        add("tblClass", classId=f"bulk-transfer-class-{class_no:03}",
            majorId="bulk-major-02", classCode=f"802-2026-T{class_no:03}",
            className=f"计算机科学与技术260{class_no:02}班", enrollmentYear=2026,
            classNumber=100 + class_no, isActive=True, rowVersion=0)

    for index in range(transfer_students):
        serial = index + 1
        class_no = index // 25 + 1
        student_id = f"bulk-transfer-student-{serial:04}"
        user_id = f"bulk-transfer-user-{serial:04}"
        name = f"转专业学生{serial:04}"
        login_id = f"21326{6000 + serial:04}"
        add("tblUser", userId=user_id, loginId=login_id,
            **people.credentials(user_id), roleCode="STUDENT", accountStatus="ACTIVE",
            mustChangePassword=True, failedLoginCount=0, lockedUntil=None,
            lastLoginAt=None, rowVersion=0, createdAt=now, updatedAt=now)
        birth = date(2007 + (serial % 2), (serial - 1) % 12 + 1,
                     (serial - 1) % 27 + 1)
        class_id = f"bulk-transfer-class-{class_no:03}"
        add("tblStudent", studentId=student_id, userId=user_id,
            studentNumber=f"9926{class_no % 10}{serial % 100:02}",
            studentType="UNDERGRADUATE", studentName=name,
            gender="男" if serial % 2 else "女", email=f"transfer{serial:04}@example.com",
            phone=f"138800{serial:05}", idDocumentType="护照",
            idDocumentNumber=f"TRANSFER{serial:08}", birthDate=birth,
            classId=class_id, enrollmentDate=date(2026, 9, 1), studentStatus="ACTIVE",
            enrolled=True, onCampus=True, campus="九龙湖校区", educationLevel="本科",
            trainingMode="普通全日制", programLengthYears=4,
            expectedGraduationDate=date(2030, 6, 30), rowVersion=0,
            createdAt=now, updatedAt=now)
        accounts.append(dict(login=login_id, password=people.PASSWORD, role="STUDENT",
                             name=name, scenario="转专业申请；学籍=ACTIVE", first_change="是"))

    add("tblMajorTransferBatch", batchId=BATCH_ID, batchName="2026秋季跨学院转专业专项批次",
        batchStatus="CLOSED", applicationStart=datetime(2026, 9, 1),
        applicationEnd=datetime(2026, 12, 31), publicityStart=None,
        publicityEnd=None, effectiveDate=datetime(2027, 2, 20), rowVersion=0,
        createdAt=now, updatedAt=now)
    add("tblMajorTransferOption", optionId=OPTION_ID, batchId=BATCH_ID,
        targetMajorId="bulk-major-03", targetDepartmentId="bulk-dept-02",
        targetMajorName="数学应用", targetDepartmentName="数学学院", grades="1",
        receiveQuota=240, interviewQuota=240, writtenPassScore=60,
        interviewPassScore=60, writtenWeightPct=60, interviewWeightPct=40,
        difficultyQuotaExempt=False, requirements="面向计算机学院大一学生开放",
        isActive=True, rowVersion=0, createdAt=now, updatedAt=now)

    for index in range(transfer_students):
        serial = index + 1
        class_no = index // 25 + 1
        student_id = f"bulk-transfer-student-{serial:04}"
        add("tblMajorTransferApplication", applicationId=f"bulk-transfer-app-{serial:04}",
            batchId=BATCH_ID, studentId=student_id, applicationType="ORDINARY",
            applicationStatus="SUBMITTED", optionId=OPTION_ID,
            fromDepartmentId="bulk-dept-01", fromDepartmentName="计算机学院",
            fromMajorId="bulk-major-02", fromMajorName="计算机科学",
            fromClassId=f"bulk-transfer-class-{class_no:03}",
            fromClassName=f"计算机科学与技术260{class_no:02}班",
            fromStudentNumber=f"9926{class_no % 10}{serial % 100:02}", fromGrade="1",
            studentName=f"转专业学生{serial:04}", reason="希望在数学建模与数据分析方向深入学习",
            writtenScore=None, interviewScore=None, finalScore=None,
            baseStudentVersion=0, applicationVersion=1, submittedAt=now,
            sourceReviewerUserId=None, sourceReviewedAt=None, sourceComment=None,
            qualificationReviewerUserId=None, qualificationReviewedAt=None,
            qualificationComment=None, createdAt=now, updatedAt=now)
    return accounts


def generate_reverse(add, now: datetime, application_count: int = 12):
    """Emit valid first-year mathematics students' applications to computer science."""
    if application_count < 10:
        raise ValueError("application_count must be at least 10")
    add("tblMajorTransferBatch", batchId=REVERSE_BATCH_ID,
        batchName="2026秋季数学学院转入计算机学院批次", batchStatus="CLOSED",
        applicationStart=datetime(2026, 9, 1), applicationEnd=datetime(2026, 12, 31),
        publicityStart=None, publicityEnd=None, effectiveDate=datetime(2027, 2, 20),
        rowVersion=0, createdAt=now, updatedAt=now)
    add("tblMajorTransferOption", optionId=REVERSE_OPTION_ID, batchId=REVERSE_BATCH_ID,
        targetMajorId="bulk-major-02", targetDepartmentId="bulk-dept-01",
        targetMajorName="计算机科学", targetDepartmentName="计算机学院", grades="1",
        receiveQuota=application_count, interviewQuota=application_count,
        writtenPassScore=60, interviewPassScore=60, writtenWeightPct=60,
        interviewWeightPct=40, difficultyQuotaExempt=False,
        requirements="面向数学学院大一学生开放", isActive=True, rowVersion=0,
        createdAt=now, updatedAt=now)
    for offset in range(application_count):
        global_index = 304 + offset * 4
        serial = (global_index - 1) % 150 + 1
        class_name = f"803261班"
        student_id = f"bulk-student-{global_index:04d}"
        student_number = f"03261{serial:03d}"
        add("tblMajorTransferApplication",
            applicationId=f"bulk-transfer-reverse-app-{offset + 1:03d}",
            batchId=REVERSE_BATCH_ID, studentId=student_id, applicationType="ORDINARY",
            applicationStatus="ASSESSED", optionId=REVERSE_OPTION_ID,
            fromDepartmentId="bulk-dept-02", fromDepartmentName="数学学院",
            fromMajorId="bulk-major-03", fromMajorName="数学应用",
            fromClassId="bulk-class-03-2026", fromClassName=class_name,
            fromStudentNumber=student_number, fromGrade="1",
            studentName=people.student_name(global_index),
            reason="希望在算法、数据科学与计算机建模方向继续学习",
            writtenScore=82 + offset % 8, interviewScore=80 + offset % 10,
            finalScore=81.2 + offset % 8, baseStudentVersion=0,
            applicationVersion=4, submittedAt=now,
            sourceReviewerUserId="00000000-0000-0000-0000-000000000004",
            sourceReviewedAt=now, sourceComment="转出学院已核实",
            qualificationReviewerUserId="00000000-0000-0000-0000-000000000013",
            qualificationReviewedAt=now, qualificationComment="资格审核通过",
            createdAt=now, updatedAt=now)

def validate_transfer_fixture(rows, application_start: date = date(2026, 9, 1)):
    """Assert generated transfer applications satisfy the public fixture contract."""
    applications = rows.get("tblMajorTransferApplication", [])
    options = {row["optionId"]: row for row in rows.get("tblMajorTransferOption", [])}
    students = {row["studentId"]: row for row in rows.get("tblStudent", [])}
    if not applications:
        raise AssertionError("transfer fixture has no applications")
    transfer_student_ids = {row["studentId"] for row in applications}
    transfer_accounts = {
        row["loginId"] for row in rows.get("tblUser", [])
        if row["userId"].startswith("bulk-transfer-user-")
    }
    expected_accounts = {f"21326{6000 + serial:04}" for serial in range(1, len(transfer_accounts) + 1)}
    if transfer_accounts != expected_accounts:
        raise AssertionError("transfer accounts are not a contiguous 2026 block")
    failed_grades = [row for row in rows.get("tblStudentGrade", [])
                     if row.get("studentId") in transfer_student_ids
                     and row.get("result") == "FAILED"]
    if failed_grades:
        raise AssertionError("transfer students cannot have failed grades")
    for row in applications:
        option = options[row["optionId"]]
        student = students[row["studentId"]]
        if row["fromDepartmentId"] == option["targetDepartmentId"]:
            raise AssertionError("transfer fixture contains same-college application")
        age = floor((application_start - student["birthDate"]).days / 365.2425)
        status = row["applicationStatus"]
        assessed_valid = status == "ASSESSED" and row.get("finalScore") is not None \
            and row.get("sourceReviewerUserId") and row.get("qualificationReviewerUserId")
        if row["fromGrade"] != "1" or age not in range(17, 21) \
                or (status != "SUBMITTED" and not assessed_valid):
            raise AssertionError("transfer fixture contains an invalid application")
