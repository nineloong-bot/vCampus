"""Five submitted mathematics-to-computing transfer applications."""
from datetime import datetime

import people

BATCH_ID = "transfer-2026-autumn"
OPTIONS = (
    ("option-cs", "major-cs", "计算机科学与技术", 3),
    ("option-se", "major-se", "软件工程", 2),
    ("option-ai", "major-ai", "人工智能", 2),
)


def generate(add, now):
    """Generate one open batch, three destinations, and five applications."""
    add("tblMajorTransferBatch", batchId=BATCH_ID, batchName="2026-2027学年秋季转专业批次",
        batchStatus="OPEN", applicationStart=datetime(2026, 9, 1),
        applicationEnd=datetime(2026, 9, 30, 23, 59, 59), publicityStart=None,
        publicityEnd=None, effectiveDate=None, rowVersion=0,
        createdAt=datetime(2026, 8, 20, 9), updatedAt=now)
    for option_id, major_id, major_name, quota in OPTIONS:
        add("tblMajorTransferOption", optionId=option_id, batchId=BATCH_ID,
            targetMajorId=major_id, targetDepartmentId="dept-cse",
            targetMajorName=major_name, targetDepartmentName="计算机科学与工程学院",
            grades="1,2,3", receiveQuota=quota, interviewQuota=quota * 2,
            writtenPassScore=60.0, interviewPassScore=60.0,
            writtenWeightPct=60, interviewWeightPct=40, difficultyQuotaExempt=False,
            requirements="已修课程无不及格记录，具备良好的数学与程序设计基础。",
            isActive=True, rowVersion=0, createdAt=datetime(2026, 8, 20, 9), updatedAt=now)
    reasons = (
        "希望系统学习计算机体系结构与算法设计，未来从事基础软件研发。",
        "对计算机系统软件与程序设计有浓厚兴趣，希望系统学习计算机科学专业课程。",
        "在数学建模中接触算法理论后，希望深入学习计算机体系结构与理论计算机科学。",
        "希望把数学基础用于算法优化、数据库系统和高性能计算研究。",
        "对计算机网络与分布式系统有浓厚兴趣，希望系统掌握计算机科学技术知识。",
    )
    option_ids = ("option-cs", "option-cs", "option-cs", "option-cs", "option-cs")
    for offset in range(5):
        cohort_serial = 25 + offset
        local = offset + 1
        name, _ = people._name(cohort_serial - 1)
        add("tblMajorTransferApplication", applicationId=f"transfer-app-{offset + 1:02d}",
            batchId=BATCH_ID, studentId=f"student-2024-{cohort_serial:03d}",
            applicationType="ORDINARY", applicationStatus="SUBMITTED",
            optionId=option_ids[offset], fromDepartmentId="dept-math",
            fromDepartmentName="数学学院", fromMajorId="major-math",
            fromMajorName="数学与应用数学", fromClassId="class-math-2024",
            fromClassName="数学与应用数学2401班", fromStudentNumber=f"701241{local:02d}",
            fromGrade="3", studentName=name, reason=reasons[offset],
            writtenScore=None, interviewScore=None, finalScore=None,
            baseStudentVersion=0, applicationVersion=1,
            submittedAt=datetime(2026, 9, 10 + offset, 10, 30),
            sourceReviewerUserId=None, sourceReviewedAt=None, sourceComment=None,
            qualificationReviewerUserId=None, qualificationReviewedAt=None,
            qualificationComment=None, createdAt=datetime(2026, 9, 8 + offset, 9),
            updatedAt=datetime(2026, 9, 10 + offset, 10, 30))


def validate_transfer_fixture(rows, application_start=None):
    """Validate the five applications against student organization snapshots."""
    applications = rows.get("tblMajorTransferApplication", [])
    options = {row["optionId"]: row for row in rows.get("tblMajorTransferOption", [])}
    students = {row["studentId"]: row for row in rows.get("tblStudent", [])}
    classes = {row["classId"]: row for row in rows.get("tblClass", [])}
    if len(applications) != 5:
        raise AssertionError("exactly five transfer applications are required")
    for application in applications:
        student = students[application["studentId"]]
        klass = classes[student["classId"]]
        option = options[application["optionId"]]
        if application["applicationStatus"] != "SUBMITTED":
            raise AssertionError("all initial transfer applications must be submitted")
        if klass["majorId"] != application["fromMajorId"]:
            raise AssertionError("transfer source major snapshot is inconsistent")
        if option["targetDepartmentId"] != "dept-cse":
            raise AssertionError("transfer target must be the computing college")
