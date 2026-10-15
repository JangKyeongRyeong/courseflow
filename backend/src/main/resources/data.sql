INSERT IGNORE INTO students(id, student_number, display_name, max_credits) VALUES
    ('00000000-0000-0000-0000-000000000001', '20240001', '김민준', 18);

INSERT IGNORE INTO courses(id, code, title, credits) VALUES
    ('10000000-0000-0000-0000-000000000001', 'CS101', '프로그래밍 기초', 3),
    ('10000000-0000-0000-0000-000000000002', 'CS201', '자료구조', 3),
    ('10000000-0000-0000-0000-000000000003', 'CS310', '데이터베이스 시스템', 3),
    ('10000000-0000-0000-0000-000000000004', 'MATH210', '이산수학', 3);

INSERT IGNORE INTO course_prerequisites(course_id, prerequisite_course_id)
SELECT c.id, p.id FROM courses c CROSS JOIN courses p WHERE c.code = 'CS201' AND p.code = 'CS101';

INSERT IGNORE INTO student_completed_courses(student_id, course_id)
SELECT s.id, c.id FROM students s CROSS JOIN courses c WHERE s.student_number = '20240001' AND c.code = 'CS101';

INSERT IGNORE INTO sections(id, term_id, course_id, section_code, instructor, capacity, enrollment_opens_at, enrollment_closes_at)
SELECT v.id, '2026-FALL', c.id, v.section_code, v.instructor, v.capacity,
       UTC_TIMESTAMP(6) - INTERVAL 1 DAY, UTC_TIMESTAMP(6) + INTERVAL 30 DAY
FROM (
    SELECT '20000000-0000-0000-0000-000000000001' AS id, '01' AS section_code, '박서연' AS instructor, 45 AS capacity, 'CS101' AS course_code
    UNION ALL SELECT '20000000-0000-0000-0000-000000000002', '01', '이도윤', 40, 'CS201'
    UNION ALL SELECT '20000000-0000-0000-0000-000000000003', '01', '최유진', 35, 'CS310'
    UNION ALL SELECT '20000000-0000-0000-0000-000000000004', '01', '정하준', 50, 'MATH210'
) v JOIN courses c ON c.code = v.course_code;

INSERT INTO section_meeting_times(section_id, day_of_week, starts_at, ends_at)
SELECT s.id, v.day_of_week, v.starts_at, v.ends_at
FROM (
    SELECT '20000000-0000-0000-0000-000000000001' AS section_id, 1 AS day_of_week, '09:00:00' AS starts_at, '10:15:00' AS ends_at
    UNION ALL SELECT '20000000-0000-0000-0000-000000000001', 3, '09:00:00', '10:15:00'
    UNION ALL SELECT '20000000-0000-0000-0000-000000000002', 2, '10:30:00', '11:45:00'
    UNION ALL SELECT '20000000-0000-0000-0000-000000000002', 4, '10:30:00', '11:45:00'
    UNION ALL SELECT '20000000-0000-0000-0000-000000000003', 1, '13:30:00', '14:45:00'
    UNION ALL SELECT '20000000-0000-0000-0000-000000000003', 3, '13:30:00', '14:45:00'
    UNION ALL SELECT '20000000-0000-0000-0000-000000000004', 2, '14:00:00', '15:15:00'
    UNION ALL SELECT '20000000-0000-0000-0000-000000000004', 4, '14:00:00', '15:15:00'
) v JOIN sections s ON s.id = v.section_id
WHERE NOT EXISTS (
    SELECT 1 FROM section_meeting_times existing
    WHERE existing.section_id = s.id AND existing.day_of_week = v.day_of_week
);
