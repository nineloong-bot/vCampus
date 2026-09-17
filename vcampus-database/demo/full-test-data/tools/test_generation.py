"""Contract tests for the deterministic vCampus release dataset."""
import unittest
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal
from unittest.mock import patch

import courses
import library
import major_transfer
import people
import shop

NOW = datetime(2026, 9, 16, 12)
BANNED = ("test", "测试", "demo", "演示", "fake", "sample", "bulk", "dummy", "example")


def capture(*generators):
    rows = defaultdict(list)
    add = lambda table, **fields: rows[table].append(fields)
    accounts = []
    for generator in generators:
        result = generator(add, NOW)
        if result:
            accounts.extend(result)
    return rows, accounts


class PeopleContractTest(unittest.TestCase):
    def setUp(self):
        fast = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast):
            self.rows, self.accounts = capture(people.generate)

    def test_exact_organization_and_student_counts(self):
        self.assertEqual(2, len(self.rows["tblDepartment"]))
        self.assertEqual(5, len(self.rows["tblMajor"]))
        self.assertEqual(15, len(self.rows["tblClass"]))
        self.assertEqual(120, len(self.rows["tblStudent"]))
        class_counts = Counter(row["classId"] for row in self.rows["tblStudent"])
        self.assertEqual(set(row["classId"] for row in self.rows["tblClass"]), set(class_counts))
        self.assertGreaterEqual(min(class_counts.values()), 5)

    def test_student_logins_are_the_three_exact_ranges(self):
        student_logins = sorted(row["loginId"] for row in self.rows["tblUser"]
                                if row["roleCode"] == "STUDENT")
        expected = [f"213{year}{serial:04d}"
                    for year in (24, 25, 26) for serial in range(1, 41)]
        self.assertEqual(expected, student_logins)
        students = [row for row in self.rows["tblUser"] if row["roleCode"] == "STUDENT"]
        self.assertTrue(all(row["mustChangePassword"] for row in students))
        sequences = {row["sequenceKey"]: row for row in self.rows["tblNumberSequence"]}
        self.assertEqual(40, sequences["CAMPUS_CARD_GLOBAL"]["currentValue"])

    def test_student_numbers_match_the_runtime_allocator_format(self):
        classes = {row["classId"]: row for row in self.rows["tblClass"]}
        majors = {row["majorId"]: row for row in self.rows["tblMajor"]}
        for student in self.rows["tblStudent"]:
            classroom = classes[student["classId"]]
            expected_prefix = (majors[classroom["majorId"]]["majorCode"]
                               + str(classroom["enrollmentYear"])[2:] + "1")
            self.assertEqual(8, len(student["studentNumber"]))
            self.assertTrue(student["studentNumber"].startswith(expected_prefix))

    def test_eight_administrator_accounts_have_expected_roles(self):
        expected = {
            "ADMIN": "SUPER_ADMIN", "STUDENT": "STUDENT_ADMIN",
            "COURSE": "COURSE_ADMIN", "LIBRARY": "LIBRARY_ADMIN",
            "SHOP": "SHOP_ADMIN", "USER": "USER_ADMIN",
            "CSADMIN": "COLLEGE_ADMIN", "MATHADMIN": "COLLEGE_ADMIN",
        }
        actual = {row["loginId"]: row["roleCode"] for row in self.rows["tblUser"]
                  if row["roleCode"].endswith("ADMIN")}
        self.assertEqual(expected, actual)
        self.assertEqual(2, len(self.rows["tblStudentCollegeAdministrator"]))

    def test_student_profiles_are_complete_and_coherent(self):
        required = ("namePinyin", "politicalStatus", "ethnicity", "maritalStatus",
                    "idDocumentType", "idDocumentNumber", "birthDate", "nativePlace",
                    "countryRegion", "birthplace", "studentOriginPlace",
                    "householdRegistrationType", "householdBeforeEnrollment",
                    "householdAfterEnrollment", "overseasChineseStatus", "religion",
                    "healthStatus", "bloodType", "weightKg", "heightCm", "specialties",
                    "hobbies", "onlyChild", "email", "phone", "campus",
                    "educationLevel", "trainingMode", "programLengthYears",
                    "attendanceMode", "expectedGraduationDate", "counselorName",
                    "counselorContact")
        classes = {row["classId"]: row for row in self.rows["tblClass"]}
        for student in self.rows["tblStudent"]:
            self.assertTrue(all(student.get(field) is not None for field in required))
            cohort = classes[student["classId"]]["enrollmentYear"]
            self.assertEqual(cohort - 18, student["birthDate"].year)
            self.assertIn(student["birthDate"].month, range(1, 9))
            self.assertTrue(people.valid_resident_id(student["idDocumentNumber"]))


class AcademicContractTest(unittest.TestCase):
    def setUp(self):
        fast = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast):
            self.rows, _ = capture(people.generate, courses.generate)

    def test_plans_cover_every_major_cohort_and_eight_semesters(self):
        self.assertEqual(15, len(self.rows["tblTrainingPlan"]))
        semesters = defaultdict(set)
        credits = set()
        for row in self.rows["tblTrainingPlanCourse"]:
            semesters[row["planId"]].add(row["semester"])
            credits.add(row["credits"])
        self.assertEqual({1, 2, 3, 4, 5, 6, 7, 8}, set.intersection(*semesters.values()))
        self.assertGreaterEqual(len(credits), 5)

    def test_every_unique_plan_course_has_one_operational_catalog_reference(self):
        plan_codes = {row["courseCode"] for row in self.rows["tblTrainingPlanCourse"]
                      if row["isActive"]}
        catalog_codes = {row["courseCode"] for row in self.rows["tblCourse"]
                         if row["isActive"]}
        self.assertEqual(plan_codes, catalog_codes)

    def test_initial_selection_state_is_empty_with_one_open_phase(self):
        self.assertEqual([], self.rows["tblEnrollment"])
        self.assertEqual([], self.rows["tblEnrollmentAdjustment"])
        open_phases = [row for row in self.rows["tblCourseSelectionPhase"]
                       if row["phaseStatus"] == "OPEN"]
        self.assertEqual(1, len(open_phases))
        self.assertEqual({0}, {row["enrolledCount"] for row in self.rows["tblCourseOffering"]})

    def test_each_selectable_course_has_two_complete_offerings(self):
        counts = Counter(row["courseId"] for row in self.rows["tblCourseOffering"])
        self.assertTrue(counts)
        self.assertEqual({2}, set(counts.values()))
        schedules = Counter(row["offeringId"] for row in self.rows["tblCourseSchedule"])
        self.assertEqual({1}, set(schedules.values()))
        self.assertTrue(all(row["teacherUserId"] and row["className"]
                            and row["capacity"] > 0 for row in self.rows["tblCourseOffering"]))

    def test_classrooms_cover_capacity_tiers_and_all_schedules_are_valid(self):
        rooms = {row["classroom"]: row for row in self.rows["tblClassroom"]}
        self.assertTrue({30, 50, 100, 200}.issubset(
            {row["capacity"] for row in rooms.values() if not row["sharedSportsVenue"]}))
        self.assertTrue(rooms["桃园操场"]["sharedSportsVenue"])
        offerings = {row["offeringId"]: row for row in self.rows["tblCourseOffering"]}
        courses_by_id = {row["courseId"]: row for row in self.rows["tblCourse"]}
        occupied = set()
        teacher_occupied = set()
        for schedule in self.rows["tblCourseSchedule"]:
            room = rooms[schedule["classroom"]]
            offering = offerings[schedule["offeringId"]]
            course = courses_by_id[offering["courseId"]]
            for week in range(schedule["startWeek"], schedule["endWeek"] + 1):
                for period in range(schedule["startPeriod"], schedule["endPeriod"] + 1):
                    teacher_key = (offering["teacherUserId"], schedule["dayOfWeek"], week, period)
                    self.assertNotIn(teacher_key, teacher_occupied)
                    teacher_occupied.add(teacher_key)
            if room["sharedSportsVenue"]:
                self.assertTrue(course["courseName"].startswith("体育"))
                continue
            self.assertLessEqual(offering["capacity"], room["capacity"])
            for week in range(schedule["startWeek"], schedule["endWeek"] + 1):
                for period in range(schedule["startPeriod"], schedule["endPeriod"] + 1):
                    key = (schedule["classroom"], schedule["dayOfWeek"], week, period)
                    self.assertNotIn(key, occupied)
                    occupied.add(key)

    def test_historical_grades_follow_cohort_rules(self):
        students = {row["studentId"]: row for row in self.rows["tblStudent"]}
        classes = {row["classId"]: row for row in self.rows["tblClass"]}
        plan_courses = {row["planCourseId"]: row for row in self.rows["tblTrainingPlanCourse"]}
        graded_semesters = defaultdict(set)
        for grade in self.rows["tblStudentGrade"]:
            cohort = classes[students[grade["studentId"]]["classId"]]["enrollmentYear"]
            graded_semesters[cohort].add(plan_courses[grade["planCourseId"]]["semester"])
        self.assertTrue({1, 2, 3, 4}.issubset(graded_semesters[2024]))
        self.assertEqual({1, 2}, graded_semesters[2025])
        self.assertNotIn(2026, graded_semesters)


class ScenarioContractTest(unittest.TestCase):
    def setUp(self):
        fast = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast):
            self.rows, _ = capture(people.generate, courses.generate,
                                   major_transfer.generate, library.generate, shop.generate)

    def test_five_submitted_math_to_computer_transfers(self):
        apps = self.rows["tblMajorTransferApplication"]
        options = {row["optionId"]: row for row in self.rows["tblMajorTransferOption"]}
        self.assertEqual(5, len(apps))
        self.assertEqual({"SUBMITTED"}, {row["applicationStatus"] for row in apps})
        self.assertEqual({"数学学院"}, {row["fromDepartmentName"] for row in apps})
        self.assertEqual({"计算机科学与工程学院"},
                         {options[row["optionId"]]["targetDepartmentName"] for row in apps})
        applicant_ids = {row["studentId"] for row in apps}
        applicant_grades = [row for row in self.rows["tblStudentGrade"]
                            if row["studentId"] in applicant_ids]
        self.assertTrue(applicant_grades)
        self.assertEqual({"PASSED"}, {row["result"] for row in applicant_grades})

    def test_transfer_batch_initializes_each_option_lifecycle(self):
        finalizations = self.rows["tblMajorTransferOptionFinalization"]
        self.assertEqual({"option-cs", "option-se", "option-ai"},
                         {row["optionId"] for row in finalizations})
        self.assertEqual({"PROCESSING"},
                         {row["finalizationStatus"] for row in finalizations})
        self.assertFalse(self.rows["tblMajorTransferBatchCollege"])

    def test_library_has_two_traceable_overdue_users(self):
        overdue = [row for row in self.rows["tblBookLoan"] if row["loanStatus"] == "OVERDUE"]
        self.assertEqual(2, len(overdue))
        self.assertTrue(all(row["overdueFine"] > Decimal("0") for row in overdue))
        self.assertEqual(2, len(library.overdue_manifest(self.rows, NOW)))

    def test_commerce_matches_required_matrix(self):
        self.assertEqual(6, len(self.rows["tblShop"]))
        self.assertEqual(Counter({"ACTIVE": 5, "SUSPENDED": 1}),
                         Counter(row["shopStatus"] for row in self.rows["tblShop"]))
        statuses = Counter(row["applicationStatus"] for row in self.rows["tblSellerApplication"])
        self.assertGreaterEqual(statuses["APPROVED"], 6)
        self.assertGreaterEqual(statuses["PENDING"], 1)
        self.assertGreaterEqual(statuses["REJECTED"], 1)
        self.assertEqual(72, len(self.rows["tblProduct"]))
        sku_counts = Counter(row["productId"] for row in self.rows["tblProductSku"])
        self.assertGreaterEqual(sum(count >= 2 for count in sku_counts.values()), 12)
        cart_sizes = sorted(Counter(row["cartId"] for row in self.rows["tblCartItem"]).values())
        self.assertEqual([2, 6], cart_sizes)
        self.assertGreaterEqual(len(self.rows["tblCart"]), 3)
        self.assertEqual(20, len(self.rows["tblOrder"]))
        self.assertGreaterEqual(len(self.rows["tblShopQualification"]), 5)
        self.assertGreaterEqual(len(self.rows["tblShopGovCase"]), 5)

    def test_all_business_text_omits_banned_markers(self):
        offenders = []
        for table, values in self.rows.items():
            for row in values:
                for field, value in row.items():
                    if isinstance(value, str) and any(word in value.lower() for word in BANNED):
                        offenders.append((table, field, value))
        self.assertEqual([], offenders)


if __name__ == "__main__":
    unittest.main()
