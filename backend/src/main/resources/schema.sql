CREATE TABLE IF NOT EXISTS students (
    id CHAR(36) PRIMARY KEY,
    student_number VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    max_credits INTEGER NOT NULL DEFAULT 18 CHECK (max_credits > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS courses (
    id CHAR(36) PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    credits INTEGER NOT NULL CHECK (credits > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS course_prerequisites (
    course_id CHAR(36) NOT NULL,
    prerequisite_course_id CHAR(36) NOT NULL,
    PRIMARY KEY (course_id, prerequisite_course_id),
    CHECK (course_id <> prerequisite_course_id),
    CONSTRAINT fk_prerequisite_course FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_required_course FOREIGN KEY (prerequisite_course_id) REFERENCES courses(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS student_completed_courses (
    student_id CHAR(36) NOT NULL,
    course_id CHAR(36) NOT NULL,
    PRIMARY KEY (student_id, course_id),
    CONSTRAINT fk_completed_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_completed_course FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sections (
    id CHAR(36) PRIMARY KEY,
    term_id VARCHAR(40) NOT NULL,
    course_id CHAR(36) NOT NULL,
    section_code VARCHAR(20) NOT NULL,
    instructor VARCHAR(120) NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    enrollment_opens_at DATETIME(6) NOT NULL,
    enrollment_closes_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_section_term_course_code (term_id, course_id, section_code),
    CHECK (enrollment_closes_at > enrollment_opens_at),
    CONSTRAINT fk_section_course FOREIGN KEY (course_id) REFERENCES courses(id),
    KEY ix_section_term (term_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS section_meeting_times (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    section_id CHAR(36) NOT NULL,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    starts_at TIME NOT NULL,
    ends_at TIME NOT NULL,
    CHECK (ends_at > starts_at),
    CONSTRAINT fk_meeting_section FOREIGN KEY (section_id) REFERENCES sections(id) ON DELETE CASCADE,
    KEY ix_meeting_section (section_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS enrollments (
    student_id CHAR(36) NOT NULL,
    section_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (student_id, section_id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_enrollment_section FOREIGN KEY (section_id) REFERENCES sections(id),
    KEY ix_enrollment_section (section_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS idempotency_records (
    student_id CHAR(36) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    section_id CHAR(36) NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (student_id, idempotency_key),
    CONSTRAINT fk_idempotency_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_idempotency_section FOREIGN KEY (section_id) REFERENCES sections(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS waitlist_entries (
    id CHAR(36) PRIMARY KEY,
    student_id CHAR(36) NOT NULL,
    section_id CHAR(36) NOT NULL,
    position BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_waitlist_student_section (student_id, section_id),
    UNIQUE KEY uq_waitlist_section_position (section_id, position),
    CONSTRAINT fk_waitlist_student FOREIGN KEY (student_id) REFERENCES students(id),
    CONSTRAINT fk_waitlist_section FOREIGN KEY (section_id) REFERENCES sections(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS waitlist_offers (
    id CHAR(36) PRIMARY KEY,
    waitlist_entry_id CHAR(36) NOT NULL UNIQUE,
    section_id CHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_offer_entry FOREIGN KEY (waitlist_entry_id) REFERENCES waitlist_entries(id),
    CONSTRAINT fk_offer_section FOREIGN KEY (section_id) REFERENCES sections(id),
    KEY ix_offer_section_status_expiry (section_id, status, expires_at)
) ENGINE=InnoDB;
