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

    def test_electronic_information_major_has_its_own_college(self):
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            rows = self.capture(people.generate)

        departments = {row["departmentId"]: row["departmentName"]
                       for row in rows["tblDepartment"]}
        majors = {row["majorId"]: row["departmentId"] for row in rows["tblMajor"]}
        self.assertEqual(9, len(departments))
        self.assertEqual("信息科学与工程学院", departments["bulk-dept-09"])
        self.assertEqual("bulk-dept-09", majors["bulk-major-12"])
        self.assertEqual("bulk-dept-06", majors["bulk-major-11"])

    def test_student_accounts_encode_their_enrollment_year(self):
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            rows = self.capture(people.generate)
        users = {row["userId"]: row["loginId"] for row in rows["tblUser"]}
        classes = {row["classId"]: row["enrollmentYear"] for row in rows["tblClass"]}
        for student in rows["tblStudent"]:
            cohort = classes[student["classId"]]
            self.assertEqual(f"213{cohort % 100:02d}", users[student["userId"]][:5])
            if cohort in {2023, 2024}:
                self.assertGreaterEqual(int(users[student["userId"]][5:]), 1001)

    def test_transfer_accounts_are_a_contiguous_2026_block(self):
        rows = self.capture(major_transfer.generate)
        accounts = [row["loginId"] for row in rows["tblUser"]
                    if row["loginId"].startswith("21326")]
        transfer_accounts = sorted(int(account) for account in accounts
                                   if account.startswith("213266"))
        self.assertEqual(list(range(213266001, 213266241)), transfer_accounts)

    def test_transfer_account_manifest_contains_all_applicants(self):
        accounts = major_transfer.generate(
            lambda table, **fields: None, datetime(2026, 9, 7, 12))
        self.assertEqual(240, len(accounts))
        self.assertEqual("213266001", accounts[0]["login"])
        self.assertEqual("213266240", accounts[-1]["login"])

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

    def test_every_generated_student_requires_an_initial_password_change(self):
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            rows = self.capture(people.generate)
        major_transfer.generate(lambda table, **fields: rows[table].append(fields),
                                datetime(2026, 9, 7, 12))
        students = [row for row in rows["tblUser"] if row["roleCode"] == "STUDENT"]
        self.assertTrue(students)
        self.assertTrue(all(row["mustChangePassword"] for row in students))

    def test_course_fixture_rejects_an_enrollment_without_exactly_one_schedule(self):
        rows = self.capture(courses.generate)
        rows["tblCourseSchedule"].pop()
        with self.assertRaises(AssertionError):
            courses.validate_course_fixture(rows)

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

    def test_library_has_a_large_past_due_cohort_for_borrowing_tests(self):
        rows = self.capture(library.generate)
        overdue = [row for row in rows["tblBookLoan"] if row["loanStatus"] == "OVERDUE"]
        self.assertGreaterEqual(len(overdue), 200)
        self.assertTrue(all(row["borrowedAt"] < datetime(2026, 9, 7, 12)
                            and row["dueAt"] < datetime(2026, 9, 7, 12)
                            for row in overdue))

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

    def test_math_college_has_a_substantial_realistic_population(self):
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            rows = self.capture(people.generate)
        majors = {row["majorId"] for row in rows["tblMajor"]
                  if row["departmentId"] == "bulk-dept-02"}
        math_students = [row for row in rows["tblStudent"] if
                         row["classId"].split("-")[2] in {"03", "04"}]
        self.assertGreaterEqual(len(math_students), 200)
        self.assertGreaterEqual(len({row["classId"] for row in math_students}), 6)
        self.assertTrue(all(row["classId"].split("-")[2] in {"03", "04"}
                            for row in math_students))

    def test_math_students_submit_valid_applications_to_computer(self):
        rows = defaultdict(list)
        now = datetime(2026, 9, 7, 12)
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            people.generate(lambda table, **fields: rows[table].append(fields), now)
        major_transfer.generate_reverse(lambda table, **fields: rows[table].append(fields), now)
        applications = rows["tblMajorTransferApplication"]
        options = {row["optionId"]: row for row in rows["tblMajorTransferOption"]}
        self.assertGreaterEqual(len(applications), 10)
        self.assertEqual({"数学学院"}, {row["fromDepartmentName"] for row in applications})
        self.assertEqual({"计算机学院"}, {options[row["optionId"]]["targetDepartmentName"]
                                         for row in applications})
        self.assertEqual({"1"}, {row["fromGrade"] for row in applications})

    def test_computer_target_batch_is_closed_and_ready_for_atomic_finalization(self):
        rows = defaultdict(list)
        now = datetime(2026, 9, 7, 12)
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            people.generate(lambda table, **fields: rows[table].append(fields), now)
        major_transfer.generate_reverse(lambda table, **fields: rows[table].append(fields), now)
        batch = next(row for row in rows["tblMajorTransferBatch"]
                     if row["batchId"] == major_transfer.REVERSE_BATCH_ID)
        applications = [row for row in rows["tblMajorTransferApplication"]
                        if row["batchId"] == major_transfer.REVERSE_BATCH_ID]
        self.assertEqual("CLOSED", batch["batchStatus"])
        self.assertEqual({"ASSESSED"}, {row["applicationStatus"] for row in applications})
        self.assertTrue(all(row["finalScore"] is not None for row in applications))

    def test_unresolved_closed_batch_remains_blocked(self):
        rows = self.capture(major_transfer.generate)
        batch = rows["tblMajorTransferBatch"][0]
        self.assertEqual("CLOSED", batch["batchStatus"])
        self.assertIn("SUBMITTED", {row["applicationStatus"]
                                    for row in rows["tblMajorTransferApplication"]})

    def test_transfer_fixture_rejects_failed_transfer_grade(self):
        rows = defaultdict(list)
        now = datetime(2026, 9, 7, 12)
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            people.generate(lambda table, **fields: rows[table].append(fields), now)
        major_transfer.generate_reverse(lambda table, **fields: rows[table].append(fields), now)
        rows["tblStudentGrade"].append({
            "studentId": rows["tblMajorTransferApplication"][0]["studentId"],
            "result": "FAILED",
        })
        with self.assertRaises(AssertionError):
            major_transfer.validate_transfer_fixture(rows)

    def test_shop_fixture_covers_named_roles_workflows_and_wallets(self):
        rows = defaultdict(list)
        now = datetime(2026, 9, 7, 12)
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            people.generate(lambda table, **fields: rows[table].append(fields), now)
        shop.generate(lambda table, **fields: rows[table].append(fields), now)

        self.assertEqual(6, len(rows["tblShop"]))
        self.assertEqual({"青禾文具铺", "拾光书屋", "梧桐生活馆", "行知运动小铺",
                          "麦香校园食坊", "晚风杂货铺"},
                         {row["shopName"] for row in rows["tblShop"]})
        self.assertEqual(72, len(rows["tblProduct"]))
        self.assertTrue(all("批量" not in row["productName"] for row in rows["tblProduct"]))
        self.assertEqual(18, len(rows["tblOrder"]))
        self.assertGreaterEqual(len(rows["tblWalletOperation"]), 10)
        self.assertGreaterEqual(len(rows["tblShopQualification"]), 4)
        self.assertGreaterEqual(len(rows["tblShopGovCase"]), 5)
        self.assertTrue(any(row["applicationStatus"] == "PENDING"
                            for row in rows["tblSellerApplication"]))
        self.assertTrue(any(row["applicationStatus"] == "REJECTED"
                            for row in rows["tblSellerApplication"]))

    def test_third_year_computer_students_have_only_first_four_semester_grades(self):
        rows = defaultdict(list)
        now = datetime(2026, 9, 7, 12)
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            people.generate(lambda table, **fields: rows[table].append(fields), now)
        courses.generate(lambda table, **fields: rows[table].append(fields), now)
        expected_semesters = {2025: 2, 2024: 4, 2023: 6}
        for cohort, max_semester in expected_semesters.items():
            student_ids = {row["studentId"] for row in rows["tblStudent"]
                           if row["studentId"].startswith("bulk-student-")
                           and row["classId"].startswith(f"bulk-class-01-{cohort}")}
            plan_courses = {row["planCourseId"]: row for row in rows["tblTrainingPlanCourse"]
                            if row["planId"] == f"bulk-plan-01-{cohort}"}
            grades = [row for row in rows["tblStudentGrade"] if row["studentId"] in student_ids]
            expected = max_semester * 5 * len(student_ids)
            self.assertTrue(student_ids)
            self.assertEqual(expected, len(grades))
            self.assertTrue(all(plan_courses[row["planCourseId"]]["semester"] <= max_semester
                                and row["result"] in {"PASSED", "FAILED"} for row in grades))
            self.assertEqual(len(grades), len({(row["studentId"], row["planCourseId"])
                                               for row in grades}))

            failed = {(row["studentId"], row["planCourseId"])
                      for row in grades if row["result"] == "FAILED"}
            retakes = {(row["studentId"], courses.plan_course_id(
                int(row["studentId"].split("-")[-1]),
                int(next(offering["courseId"] for offering in rows["tblCourseOffering"]
                         if offering["offeringId"] == row["offeringId"]).split("-")[-1])))
                       for row in rows["tblEnrollment"]
                       if row["studentId"] in student_ids
                       and row["enrollmentType"] == "RETAKE"}
            self.assertEqual(retakes, failed)

        freshman_ids = {row["studentId"] for row in rows["tblStudent"]
                        if row["studentId"].startswith("bulk-student-")
                        and row["classId"].startswith("bulk-class-01-2026")}
        self.assertFalse([row for row in rows["tblStudentGrade"]
                          if row["studentId"] in freshman_ids])


if __name__ == "__main__":
    unittest.main()
