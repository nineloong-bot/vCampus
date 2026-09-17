package edu.seu.vcampus.server.course.repository;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/** Access/JDBC implementation that composes focused course-table repositories. */
public final class AccessCourseRepository implements CourseRepository {
    private final AccessCatalogRepository catalog = new AccessCatalogRepository();
    private final AccessSelectionPhaseRepository phases = new AccessSelectionPhaseRepository();
    private final AccessOfferingRepository offerings = new AccessOfferingRepository();
    private final AccessEnrollmentRepository enrollments = new AccessEnrollmentRepository();
    private final AccessAuditRepository audits = new AccessAuditRepository();
    private final AccessClassroomRepository classrooms = new AccessClassroomRepository();

    @Override public Term insertTerm(Connection c, Term value) { return catalog.insertTerm(c, value); }
    @Override public Term requireTerm(Connection c, String id) { return catalog.requireTerm(c, id); }
    @Override public List<Term> findTerms(Connection c) { return catalog.findTerms(c); }
    @Override public Term updateTerm(Connection c, Term value, long version) {
        return catalog.updateTerm(c, value, version);
    }
    @Override public SelectionPhase insertSelectionPhase(Connection c, SelectionPhase value) {
        return phases.insert(c, value);
    }
    @Override public SelectionPhase requireSelectionPhase(Connection c, String id) { return phases.require(c, id); }
    @Override public List<SelectionPhase> findSelectionPhases(Connection c) { return phases.findAll(c); }
    @Override public Optional<SelectionPhase> findOpenSelectionPhase(Connection c) { return phases.findOpen(c); }
    @Override public SelectionPhase updateSelectionPhase(Connection c, SelectionPhase value, long version) {
        return phases.update(c, value, version);
    }
    @Override public Course insertCourse(Connection c, Course value) { return catalog.insertCourse(c, value); }
    @Override public Course requireCourse(Connection c, String id) { return catalog.requireCourse(c, id); }
    @Override public List<Course> findCourses(Connection c) { return catalog.findCourses(c); }
    @Override public List<Classroom> findClassrooms(Connection c, String keyword, int capacity, int limit) {
        return classrooms.search(c, keyword, capacity, limit);
    }
    @Override public Optional<Classroom> findClassroom(Connection c, String room) {
        return classrooms.find(c, room);
    }
    @Override public List<Schedule> findClassroomSchedules(Connection c, String termId,
                                                           String room, String excluded) {
        return classrooms.schedules(c, termId, room, excluded);
    }
    @Override public Course updateCourse(Connection c, Course value, long version) {
        return catalog.updateCourse(c, value, version);
    }
    @Override public Offering insertOffering(Connection c, Offering value, List<Schedule> schedules) {
        return offerings.insertOffering(c, value, schedules);
    }
    @Override public Offering requireOffering(Connection c, String id) { return offerings.requireOffering(c, id); }
    @Override public List<Offering> findOfferingsByTerm(Connection c, String termId) {
        return offerings.findOfferingsByTerm(c, termId);
    }
    @Override public OfferingSearchPage searchOfferings(Connection c, OfferingSearchCriteria criteria) {
        return offerings.searchOfferings(c, criteria);
    }
    @Override public Offering updateOffering(Connection c, Offering value, long version,
                                             List<Schedule> schedules) {
        return offerings.updateOffering(c, value, version, schedules);
    }
    @Override public List<Schedule> findSchedules(Connection c, String offeringId) {
        return offerings.findSchedules(c, offeringId);
    }
    @Override public Optional<Enrollment> findEnrollment(Connection c, String studentId, String offeringId) {
        return enrollments.findEnrollment(c, studentId, offeringId);
    }
    @Override public boolean existsEnrollmentForOffering(Connection c, String offeringId) {
        return enrollments.existsEnrollmentForOffering(c, offeringId);
    }
    @Override public Enrollment requireEnrollment(Connection c, String enrollmentId) {
        return enrollments.requireEnrollment(c, enrollmentId);
    }
    @Override public List<Enrollment> findActiveByStudentAndTerm(Connection c, String studentId, String termId) {
        return enrollments.findActiveByStudentAndTerm(c, studentId, termId);
    }
    @Override public List<Enrollment> findByStudentAndTerm(Connection c, String studentId, String termId) {
        return enrollments.findByStudentAndTerm(c, studentId, termId);
    }
    @Override public List<Enrollment> findActiveByStudent(Connection c,String studentId){return enrollments.findActiveByStudent(c,studentId);}
    @Override public List<Offering> findOfferingsByTeacher(Connection c,String teacherUserId){return offerings.findOfferingsByTeacher(c,teacherUserId);}
    @Override public Enrollment insertEnrollment(Connection c, Enrollment value) {
        return enrollments.insertEnrollment(c, value);
    }
    @Override public Enrollment updateEnrollment(Connection c, Enrollment value, long version) {
        return enrollments.updateEnrollment(c, value, version);
    }
    @Override public Offering changeEnrolledCount(Connection c, String id, int delta) {
        return enrollments.changeEnrolledCount(c, id, delta);
    }
    @Override public Offering incrementNormalEnrollmentForAdmin(Connection c, String id) {
        return offerings.incrementNormalEnrollmentForAdmin(c, id);
    }
    @Override public RetakeQuota findRetakeQuota(Connection c, String id) {
        return enrollments.findRetakeQuota(c, id);
    }
    @Override public RetakeQuota saveRetakeCapacity(Connection c, String id, int capacity) {
        return enrollments.saveRetakeCapacity(c, id, capacity);
    }
    @Override public Offering changeEnrolledCount(Connection c, String id, String type, int delta) {
        return enrollments.changeEnrolledCount(c, id, type, delta);
    }
    @Override public EnrollmentAdjustment insertAdjustment(Connection c, EnrollmentAdjustment value) {
        return audits.insertAdjustment(c, value);
    }
    @Override public List<EnrollmentAdjustment> findAdjustmentsByStudent(Connection c, String studentId) {
        return audits.findAdjustmentsByStudent(c, studentId);
    }
    @Override public List<EnrollmentAdjustment> findAdjustments(Connection c){return audits.findAdjustments(c);}
}
