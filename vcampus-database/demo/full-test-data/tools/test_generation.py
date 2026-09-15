"""Behavior tests for the deterministic full Access dataset generator."""
import unittest
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal
from unittest.mock import patch

import courses
import library
import people
import shop
import major_transfer


class GenerationTest(unittest.TestCase):
    def capture(self, generator):
        rows = defaultdict(list)
        generator(lambda table, **fields: rows[table].append(fields),
                  datetime(2026, 9, 7, 12))
        return rows

    def test_students_cover_four_cohorts_and_sixteen_majors_evenly(self):
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            rows = self.capture(people.generate)
        class_year = {row["classId"]: row["enrollmentYear"] for row in rows["tblClass"]}
        class_major = {row["classId"]: row["majorId"] for row in rows["tblClass"]}
        cohorts = Counter(class_year[row["classId"]] for row in rows["tblStudent"])
        majors = Counter(class_major[row["classId"]] for row in rows["tblStudent"])

        self.assertEqual({2023: 600, 2024: 600, 2025: 600, 2026: 600}, cohorts)
        self.assertEqual({150}, set(majors.values()))
        self.assertEqual(64, len(rows["tblClass"]))

    def test_testadmin_is_the_super_admin(self):
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            rows = self.capture(people.generate)

        testadmin = next(row for row in rows["tblUser"] if row["loginId"] == "TESTADMIN")
        self.assertEqual("SUPER_ADMIN", testadmin["roleCode"])

    def test_second_user_administrator_is_available_for_governance_testing(self):
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            rows = self.capture(people.generate)

        account = next(row for row in rows["tblUser"] if row["loginId"] == "USER_ADMIN_2")
        self.assertEqual("USER_ADMIN", account["roleCode"])
        self.assertEqual("ACTIVE", account["accountStatus"])
        self.assertFalse(account["mustChangePassword"])

    def test_only_last_ten_students_require_an_initial_password_change(self):
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            rows = self.capture(people.generate)
        students = [row for row in rows["tblUser"] if row["roleCode"] == "STUDENT"]
        self.assertEqual(10, sum(row["mustChangePassword"] for row in students))

    def test_courses_include_two_seasons_and_canonical_eight_term_plans(self):
        rows = self.capture(courses.generate)
        seasons = {row["season"] for row in rows["tblTerm"]}
        catalog_codes = {row["courseCode"] for row in rows["tblCourse"]}
        plan_codes = {row["courseCode"] for row in rows["tblTrainingPlanCourse"]}

        self.assertEqual({"AUTUMN", "SPRING"}, seasons)
        self.assertEqual(64, len(rows["tblTrainingPlan"]))
        self.assertEqual(2_560, len(rows["tblTrainingPlanCourse"]))
        self.assertEqual(catalog_codes, plan_codes)
        self.assertEqual(160, len(catalog_codes))
        self.assertGreaterEqual(len(rows["tblCourseOffering"]), 380)
        self.assertGreater(len(rows["tblTrainingPlanPrerequisite"]), 0)

    def test_every_course_has_an_opening_college(self):
        rows = self.capture(courses.generate)
        self.assertTrue(all(row.get("departmentId") and row.get("departmentName")
                            for row in rows["tblCourse"]))

    def test_each_plan_has_fifteen_credits_in_semesters_one_to_eight(self):
        rows = self.capture(courses.generate)
        credits = defaultdict(lambda: Decimal("0"))
        for row in rows["tblTrainingPlanCourse"]:
            if 1 <= row["semester"] <= 8:
                credits[(row["planId"], row["semester"])] += row["credits"]
        self.assertEqual({Decimal("15.0")}, set(credits.values()))
        self.assertEqual(64 * 8, len(credits))

    def test_every_offering_has_five_independent_retake_seats(self):
        rows = self.capture(courses.generate)
        quotas = {row["offeringId"]: row for row in rows["tblCourseRetakeQuota"]}
        normal_counts = Counter(row["offeringId"] for row in rows["tblEnrollment"]
                                if row["enrollmentStatus"] == "ACTIVE"
                                and row["enrollmentType"] == "NORMAL")
        retake_counts = Counter(row["offeringId"] for row in rows["tblEnrollment"]
                                if row["enrollmentStatus"] == "ACTIVE"
                                and row["enrollmentType"] == "RETAKE")

        self.assertEqual(len(rows["tblCourseOffering"]), len(quotas))
        self.assertEqual({5}, {row["capacity"] for row in quotas.values()})
        self.assertTrue(all(row["enrolledCount"] <= 5 for row in quotas.values()))
        for offering in rows["tblCourseOffering"]:
            self.assertEqual(normal_counts[offering["offeringId"]], offering["enrolledCount"])
            self.assertEqual(retake_counts[offering["offeringId"]],
                             quotas[offering["offeringId"]]["enrolledCount"])

    def test_course_display_data_does_not_contain_test_marker(self):
        rows = self.capture(courses.generate)
        visible_fields = {
            "tblCourse": ("courseName", "description"),
            "tblCourseOffering": ("className",),
            "tblCourseSchedule": ("classroom",),
            "tblCourseSelectionPhase": ("displayTitle",),
        }

        for table, fields in visible_fields.items():
            for row in rows[table]:
                for field in fields:
                    self.assertNotIn("测试", row[field])

    def test_all_generated_database_text_omits_test_marker(self):
        rows = defaultdict(list)
        add = lambda table, **fields: rows[table].append(fields)
        now = datetime(2026, 9, 7, 12)
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            people.generate(add, now)
        courses.generate(add, now)
        library.generate(add, now)
        shop.generate(add, now)

        visible_values = [value for table in rows.values() for row in table
                          for value in row.values() if isinstance(value, str)]
        self.assertFalse([value for value in visible_values if "测试" in value])

    def test_active_normal_enrollments_follow_each_students_current_plan_term(self):
        rows = self.capture(courses.generate)
        offering_courses = {
            row["offeringId"]: row["courseId"] for row in rows["tblCourseOffering"]
        }
        for enrollment in rows["tblEnrollment"]:
            if (enrollment["enrollmentStatus"] != "ACTIVE"
                    or enrollment["enrollmentType"] != "NORMAL"):
                continue
            student = int(enrollment["studentId"].rsplit("-", 1)[1])
            cohort = 2023 + (student - 1) % 4
            academic_year = 2026 - cohort + 1
            course = int(offering_courses[enrollment["offeringId"]].rsplit("-", 1)[1])
            semester = (course - 1) // 20 + 1
            self.assertEqual((academic_year - 1) * 2 + 1, semester)

    def test_major_transfer_fixture_has_many_valid_computer_to_math_applications(self):
        rows = self.capture(major_transfer.generate)
        applications = rows["tblMajorTransferApplication"]
        options = {row["optionId"]: row for row in rows["tblMajorTransferOption"]}
        self.assertGreaterEqual(len(applications), 200)
        self.assertEqual({"SUBMITTED"}, {row["applicationStatus"] for row in applications})
        self.assertEqual({"计算机学院"}, {row["fromDepartmentName"] for row in applications})
        self.assertEqual({"数学学院"}, {options[row["optionId"]]["targetDepartmentName"]
                                         for row in applications})
        self.assertEqual({"1"}, {row["fromGrade"] for row in applications})
        self.assertTrue(all(row["fromDepartmentId"] != options[row["optionId"]]["targetDepartmentId"]
                            for row in applications))


if __name__ == "__main__":
    unittest.main()
