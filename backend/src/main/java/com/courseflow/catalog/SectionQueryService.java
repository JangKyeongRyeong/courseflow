package com.courseflow.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SectionQueryService {
    private final JdbcTemplate jdbc;

    public SectionQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SectionView> listSections(String termId, String studentNumber) {
        List<SectionBase> sections = jdbc.query("""
                SELECT s.id, s.term_id, c.code AS course_code, c.title, c.credits,
                       s.section_code, s.instructor, s.capacity,
                       COALESCE(enrollment_counts.enrolled, 0) AS enrolled,
                       (mine.student_id IS NOT NULL) AS enrolled_by_me
                FROM sections s JOIN courses c ON c.id = s.course_id
                LEFT JOIN (SELECT e.section_id, count(*) AS enrolled FROM enrollments e
                           JOIN sections enrolled_section ON enrolled_section.id = e.section_id
                           WHERE enrolled_section.term_id = ? GROUP BY e.section_id)
                    enrollment_counts ON enrollment_counts.section_id = s.id
                LEFT JOIN students st ON st.student_number = ?
                LEFT JOIN enrollments mine ON mine.section_id = s.id AND mine.student_id = st.id
                WHERE s.term_id = ? ORDER BY c.code, s.section_code
                """, (rs, row) -> new SectionBase(
                UUID.fromString(rs.getString("id")), rs.getString("term_id"), rs.getString("course_code"),
                rs.getString("title"), rs.getInt("credits"), rs.getString("section_code"),
                rs.getString("instructor"), rs.getInt("capacity"), rs.getLong("enrolled"),
                rs.getBoolean("enrolled_by_me")), termId, studentNumber, termId);

        List<MeetingRow> meetingRows = jdbc.query("""
                SELECT m.section_id, m.day_of_week, m.starts_at, m.ends_at
                FROM section_meeting_times m JOIN sections s ON s.id = m.section_id
                WHERE s.term_id = ? ORDER BY s.id, m.day_of_week, m.starts_at
                """, (rs, row) -> new MeetingRow(UUID.fromString(rs.getString("section_id")), new MeetingView(
                DayOfWeek.of(rs.getInt("day_of_week")), rs.getTime("starts_at").toLocalTime(),
                rs.getTime("ends_at").toLocalTime())), termId);
        Map<UUID, List<MeetingView>> meetingsBySection = new HashMap<>();
        for (MeetingRow meeting : meetingRows) {
            meetingsBySection.computeIfAbsent(meeting.sectionId(), ignored -> new ArrayList<>()).add(meeting.meeting());
        }
        return sections.stream().map(section -> new SectionView(section.id(), section.termId(),
                section.courseCode(), section.title(), section.credits(), section.sectionCode(),
                section.instructor(), section.capacity(), section.enrolled(), section.enrolledByMe(),
                Math.max(0, section.capacity() - section.enrolled()),
                meetingsBySection.getOrDefault(section.id(), List.of()))).toList();
    }

    public record SectionView(UUID id, String termId, String courseCode, String title, int credits,
                              String sectionCode, String instructor, int capacity, long enrolled,
                              boolean enrolledByMe, long remainingSeats, List<MeetingView> meetings) {}
    public record MeetingView(DayOfWeek day, LocalTime startsAt, LocalTime endsAt) {}
    private record SectionBase(UUID id, String termId, String courseCode, String title, int credits,
                               String sectionCode, String instructor, int capacity, long enrolled,
                               boolean enrolledByMe) {}
    private record MeetingRow(UUID sectionId, MeetingView meeting) {}
}

