package com.courseflow.enrollment;

import com.courseflow.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class EnrollmentService {
    private final JdbcTemplate jdbc;

    public EnrollmentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public EnrollmentResult enroll(UUID sectionId, String idempotencyKey, EnrollRequest request) {
        UUID studentId = studentId(request.studentNumber());
        SectionState section = lockSection(sectionId);
        lockStudent(studentId);

        var previous = jdbc.query("""
                SELECT section_id, outcome FROM idempotency_records
                WHERE student_id = ? AND idempotency_key = ?
                """, (rs, row) -> new IdempotencyState(
                UUID.fromString(rs.getString("section_id")), rs.getString("outcome")), studentId.toString(), idempotencyKey);
        if (!previous.isEmpty()) {
            IdempotencyState state = previous.get(0);
            if (!state.sectionId().equals(sectionId)) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "이 요청 키는 다른 분반에 사용됐습니다.");
            }
            if (state.outcome().equals("ENROLLED")) return new EnrollmentResult(sectionId, "ENROLLED", true);
        }

        Instant now = Instant.now();
        if (now.isBefore(section.opensAt()) || now.isAfter(section.closesAt())) {
            throw ApiException.conflict("ENROLLMENT_CLOSED", "현재 수강신청 기간이 아닙니다.");
        }
        if (jdbc.queryForObject("SELECT count(*) FROM enrollments WHERE student_id = ? AND section_id = ?",
                Long.class, studentId.toString(), sectionId.toString()) > 0) {
            remember(studentId, idempotencyKey, sectionId, "ENROLLED");
            return new EnrollmentResult(sectionId, "ENROLLED", true);
        }
        long sameCourse = jdbc.queryForObject("""
                SELECT count(*) FROM enrollments e
                JOIN sections existing ON existing.id = e.section_id
                WHERE e.student_id = ? AND existing.term_id = ? AND existing.course_id = ?
                """, Long.class, studentId.toString(), section.termId(), section.courseId().toString());
        if (sameCourse > 0) throw ApiException.conflict("COURSE_ALREADY_ENROLLED", "같은 과목의 다른 분반을 이미 신청했습니다.");
        checkPrerequisites(studentId, section.courseId());
        checkSchedule(studentId, sectionId);
        checkCreditLimit(studentId, section.termId(), section.credits());

        long enrolled = jdbc.queryForObject("SELECT count(*) FROM enrollments WHERE section_id = ?", Long.class, sectionId.toString());
        long reserved = jdbc.queryForObject("""
                SELECT count(*) FROM waitlist_offers WHERE section_id = ? AND status = 'PENDING' AND expires_at > now()
                """, Long.class, sectionId.toString());
        if (enrolled + reserved >= section.capacity()) {
            throw ApiException.conflict("SECTION_FULL", "분반 정원이 가득 찼습니다.");
        }
        jdbc.update("INSERT INTO enrollments(student_id, section_id) VALUES (?, ?)", studentId.toString(), sectionId.toString());
        remember(studentId, idempotencyKey, sectionId, "ENROLLED");
        return new EnrollmentResult(sectionId, "ENROLLED", false);
    }

    @Transactional
    public void drop(UUID sectionId, String studentNumber) {
        UUID studentId = studentId(studentNumber);
        lockSection(sectionId);
        lockStudent(studentId);
        int deleted = jdbc.update("DELETE FROM enrollments WHERE student_id = ? AND section_id = ?",
                studentId.toString(), sectionId.toString());
        if (deleted == 0) throw ApiException.notFound("ENROLLMENT_NOT_FOUND", "취소할 수강 신청을 찾을 수 없습니다.");
        // Seat-offer creation from the waitlist is the next implementation slice.
    }

    private UUID studentId(String studentNumber) {
        var ids = jdbc.query("SELECT id FROM students WHERE student_number = ?",
                (rs, row) -> UUID.fromString(rs.getString("id")), studentNumber);
        if (ids.isEmpty()) throw ApiException.notFound("STUDENT_NOT_FOUND", "학생을 찾을 수 없습니다.");
        return ids.get(0);
    }

    private SectionState lockSection(UUID id) {
        var sections = jdbc.query("""
                SELECT s.id, s.term_id, s.course_id, s.capacity, s.enrollment_opens_at,
                       s.enrollment_closes_at, c.credits
                FROM sections s JOIN courses c ON c.id = s.course_id WHERE s.id = ? FOR UPDATE
                """, (rs, row) -> new SectionState(UUID.fromString(rs.getString("id")), rs.getString("term_id"),
                UUID.fromString(rs.getString("course_id")), rs.getInt("capacity"), rs.getInt("credits"),
                rs.getObject("enrollment_opens_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getObject("enrollment_closes_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)), id.toString());
        if (sections.isEmpty()) throw ApiException.notFound("SECTION_NOT_FOUND", "분반을 찾을 수 없습니다.");
        return sections.get(0);
    }

    private void lockStudent(UUID id) {
        jdbc.queryForObject("SELECT id FROM students WHERE id = ? FOR UPDATE", String.class, id.toString());
    }

    private void checkPrerequisites(UUID studentId, UUID courseId) {
        long missing = jdbc.queryForObject("""
                SELECT count(*) FROM course_prerequisites p
                WHERE p.course_id = ? AND NOT EXISTS (
                    SELECT 1 FROM student_completed_courses c
                    WHERE c.student_id = ? AND c.course_id = p.prerequisite_course_id)
                """, Long.class, courseId.toString(), studentId.toString());
        if (missing > 0) throw ApiException.conflict("PREREQUISITE_MISSING", "선수 과목을 이수하지 않았습니다.");
    }

    private void checkSchedule(UUID studentId, UUID sectionId) {
        long conflicts = jdbc.queryForObject("""
                SELECT count(*) FROM enrollments e
                JOIN section_meeting_times existing_meeting ON existing_meeting.section_id = e.section_id
                JOIN section_meeting_times requested_time ON requested_time.section_id = ?
                WHERE e.student_id = ?
                  AND existing_meeting.day_of_week = requested_time.day_of_week
                  AND existing_meeting.starts_at < requested_time.ends_at
                  AND requested_time.starts_at < existing_meeting.ends_at
                """, Long.class, sectionId.toString(), studentId.toString());
        if (conflicts > 0) throw ApiException.conflict("SCHEDULE_CONFLICT", "기존 신청과 수업 시간이 겹칩니다.");
    }

    private void checkCreditLimit(UUID studentId, String termId, int addedCredits) {
        Integer max = jdbc.queryForObject("SELECT max_credits FROM students WHERE id = ?", Integer.class, studentId.toString());
        Integer current = jdbc.queryForObject("""
                SELECT coalesce(sum(c.credits), 0) FROM enrollments e
                JOIN sections s ON s.id = e.section_id JOIN courses c ON c.id = s.course_id
                WHERE e.student_id = ? AND s.term_id = ?
                """, Integer.class, studentId.toString(), termId);
        if (current + addedCredits > max) throw ApiException.conflict("CREDIT_LIMIT_EXCEEDED", "최대 신청 학점을 초과합니다.");
    }

    private void remember(UUID studentId, String key, UUID sectionId, String outcome) {
        jdbc.update("""
                INSERT IGNORE INTO idempotency_records(student_id, idempotency_key, section_id, outcome)
                VALUES (?, ?, ?, ?)
                """, studentId.toString(), key, sectionId.toString(), outcome);
    }

    public record EnrollRequest(String studentNumber) {}
    public record EnrollmentResult(UUID sectionId, String status, boolean replayed) {}
    private record SectionState(UUID id, String termId, UUID courseId, int capacity, int credits,
                                Instant opensAt, Instant closesAt) {}
    private record IdempotencyState(UUID sectionId, String outcome) {}
}
