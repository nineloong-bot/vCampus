"""Behavior tests for the deterministic full Access dataset generator."""
import unittest
from collections import Counter, defaultdict
from datetime import datetime
from unittest.mock import patch

import courses
import people


class GenerationTest(unittest.TestCase):
    def capture(self, generator):
        rows = defaultdict(list)
        generator(lambda table, **fields: rows[table].append(fields),
                  datetime(2026, 9, 7, 12))
        return rows

    def test_students_cover_four_cohorts_and_ten_majors_evenly(self):
        fast_password = dict(passwordHash="hash", passwordSalt="salt", passwordIterations=1)
        with patch.object(people, "credentials", return_value=fast_password):
            rows = self.capture(people.generate)
        class_year = {row["classId"]: row["enrollmentYear"] for row in rows["tblClass"]}
        class_major = {row["classId"]: row["majorId"] for row in rows["tblClass"]}
        cohorts = Counter(class_year[row["classId"]] for row in rows["tblStudent"])
        majors = Counter(class_major[row["classId"]] for row in rows["tblStudent"])

        self.assertEqual({2023: 250, 2024: 250, 2025: 250, 2026: 250}, cohorts)
        self.assertEqual({100}, set(majors.values()))
        self.assertEqual(40, len(rows["tblClass"]))

    def test_courses_include_three_seasons_and_canonical_plans(self):
        rows = self.capture(courses.generate)
        seasons = {row["season"] for row in rows["tblTerm"]}
        catalog_codes = {row["courseCode"] for row in rows["tblCourse"]}
        plan_codes = {row["courseCode"] for row in rows["tblTrainingPlanCourse"]}

        self.assertEqual({"SUMMER", "AUTUMN", "SPRING"}, seasons)
        self.assertEqual(40, len(rows["tblTrainingPlan"]))
        self.assertEqual(4_800, len(rows["tblTrainingPlanCourse"]))
        self.assertEqual(catalog_codes, plan_codes)
        self.assertGreaterEqual(len(rows["tblCourseOffering"]), 290)
        self.assertGreater(len(rows["tblTrainingPlanPrerequisite"]), 0)

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
            cohort = 2023 + ((student - 1) % 100) // 25
            academic_year = 2026 - cohort + 1
            course = int(offering_courses[enrollment["offeringId"]].rsplit("-", 1)[1])
            semester = ((course - 1) // 30) * 3 + ((course - 1) % 30) // 10 + 1
            self.assertEqual((academic_year - 1) * 3 + 2, semester)


if __name__ == "__main__":
    unittest.main()
