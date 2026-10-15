# MVP Domain Model and API Draft

This is a first implementation draft based on the confirmed MVP scope. University-specific policy and peak traffic assumptions remain open.

## Core records

- **Student**: university identifier, status, and term-specific academic standing.
- **Term**: enrollment dates and policy settings such as maximum credits.
- **Course**: catalog identity, title, credits, and prerequisite rules.
- **Section**: course offering in a term, capacity, instructor, and meeting times.
- **Enrollment**: a student's confirmed seat in a section.
- **WaitlistEntry**: a student's ordered request for a full section, with a unique position/state.
- **WaitlistOffer**: a time-limited offer for an available seat, with status and an acceptance deadline.
- **QueueAdmission**: short-lived admission token and queue state for the enrollment peak. It controls service access and does not reserve a seat.

## Enrollment rules

1. A student can have at most one active enrollment per course in a term and at most one active waitlist entry for a section.
2. A section cannot have more active enrollments than its capacity. The database transaction is the final authority for this invariant.
3. Enrollment checks term window, student eligibility, prerequisites, credit limit, duplicate enrollment, and meeting-time conflicts.
4. A full section may accept a waitlist entry if the student passes applicable eligibility checks. Credit and schedule checks are repeated when the student accepts an offer.
5. When a seat opens, the next eligible waitlisted student receives an offer with a configurable acceptance deadline. The offered seat is temporarily reserved until acceptance or expiry so another request cannot claim it.
6. On acceptance, recheck eligibility and atomically convert the reservation into an enrollment. On expiry or rejection, release the reservation and offer the seat to the next eligible student. Retries must not create duplicate enrollment or skip the queue.
7. Queue admission is short-lived and rate-limited. It is not evidence that a section has a seat available.

## Initial API shape

All paths are versioned under `/api/v1`. These are resource and operation proposals, not a finalized contract.

- `GET /terms/{termId}/sections` — browse and filter section offerings.
- `GET /sections/{sectionId}` — view capacity, remaining seats, schedule, and prerequisites.
- `POST /queue/admissions` — obtain or check virtual waiting-room state.
- `POST /sections/{sectionId}/enrollments` — request enrollment; require an idempotency key.
- `DELETE /sections/{sectionId}/enrollments/me` — drop the current student's enrollment.
- `POST /sections/{sectionId}/waitlist` — join the waitlist.
- `DELETE /sections/{sectionId}/waitlist/me` — leave the waitlist.
- `GET /students/me/schedule?termId={termId}` — view confirmed enrollments and waitlist status.

## Open policy questions

- Does joining the waitlist require passing schedule-conflict and credit-limit checks at that moment?
- When a waitlisted student is ineligible at promotion, should they be skipped or kept in place?
- What should the default acceptance window be? Keep it configurable by term or section.
- What are the retry and ordering rules if queue clients disconnect or refresh?
- What is the expected peak request rate and enrollment-window duration for 5,000 concurrent users?

## Data integrity notes

- Enforce unique active enrollment per student and section in the database.
- Use transactional capacity checks and a concurrency-safe seat allocation strategy; never rely on a frontend count.
- Count active seat reservations from unexpired waitlist offers against section capacity.
- Make enrollment and queue operations safe to retry using idempotency keys and durable operation state.
- Keep queue ordering state separate from authoritative enrollment records.

## 한국어 안내

이 문서는 현재까지 확정한 MVP 범위를 바탕으로 작성한 첫 구현 초안입니다. 대학별 정책과 피크 시간대 트래픽 조건은 아직 정해지지 않았습니다.

### 핵심 데이터

- **학생(Student):** 대학 식별자, 상태, 학기별 학적 정보
- **학기(Term):** 수강신청 기간과 학기별 최대 학점 등 정책 설정
- **강좌(Course):** 교과목 식별 정보, 이름, 학점, 선수 과목 규칙
- **분반(Section):** 학기별 개설 정보, 정원, 담당 교수, 수업 시간
- **수강 신청(Enrollment):** 분반 좌석을 확정한 학생의 신청 정보
- **대기 신청(WaitlistEntry):** 정원이 찬 분반의 대기 신청 순서와 상태
- **대기 등록 제안(WaitlistOffer):** 수락 기한과 상태가 있는 좌석 제안
- **대기실 입장(QueueAdmission):** 피크 시간에 발급하는 만료 시간이 짧은 입장 토큰과 대기 상태. 서비스 진입만 제어하며 좌석은 확보하지 않습니다.

### 수강신청 규칙

1. 학생은 같은 분반에 유효한 수강 신청을 두 번 하거나 대기 신청을 두 번 할 수 없습니다.
2. 분반의 유효 수강 인원은 정원을 초과할 수 없습니다. 이 규칙은 데이터베이스 트랜잭션으로 최종 보장합니다.
3. 신청 시 학기 신청 기간, 학생 자격, 선수 과목, 학점 제한, 중복 신청, 수업 시간 충돌을 확인합니다.
4. 정원이 찬 분반은 대기 신청을 받을 수 있습니다. 대기 신청 시 어떤 자격 조건을 확인할지는 대학 정책으로 확정해야 합니다.
5. 좌석이 나면 다음 순서의 적격 대기자에게 설정 가능한 수락 기한과 함께 등록 제안을 보냅니다. 수락 또는 만료 전까지 제안된 좌석을 임시 확보해 다른 요청이 가져가지 못하도록 합니다.
6. 학생이 수락하면 자격을 다시 확인하고, 좌석 확보를 수강 신청으로 원자적으로 전환합니다. 기한이 지나거나 학생이 거절하면 좌석 확보를 해제하고 다음 적격 대기자에게 제안합니다. 재시도해도 중복 신청이 생기거나 대기 순서를 건너뛰지 않아야 합니다.
7. 가상 대기실 입장 권한은 짧게 만료되며 요청을 제한합니다. 입장 권한은 분반 좌석을 보장하지 않습니다.

### 초기 API 구성안

모든 경로는 `/api/v1` 아래에 둡니다. 다음은 리소스와 작업에 대한 제안이며, 최종 API 계약은 아닙니다.

- `GET /terms/{termId}/sections` — 분반 목록 조회 및 필터링
- `GET /sections/{sectionId}` — 정원, 잔여 좌석, 수업 시간, 선수 과목 조회
- `POST /queue/admissions` — 가상 대기실 입장 상태 확인 또는 발급
- `POST /sections/{sectionId}/enrollments` — 수강 신청. 멱등성 키가 필요합니다.
- `DELETE /sections/{sectionId}/enrollments/me` — 현재 학생의 신청 취소
- `POST /sections/{sectionId}/waitlist` — 대기 신청 등록
- `DELETE /sections/{sectionId}/waitlist/me` — 현재 학생의 대기 신청 취소
- `GET /students/me/schedule?termId={termId}` — 현재 학기의 확정 신청과 대기 상태 조회
- `POST /sections/{sectionId}/waitlist-offers/{offerId}/accept` — 등록 제안 수락
- `POST /sections/{sectionId}/waitlist-offers/{offerId}/decline` — 등록 제안 거절

### 결정이 더 필요한 정책

- 대기 신청 시점에도 시간표 충돌과 학점 제한을 확인할까요?
- 승격 시 자격이 없는 학생은 건너뛸까요, 아니면 순서를 유지할까요?
- 수락 기한의 기본값은 얼마로 할까요? 학기나 분반별 설정이 가능하도록 합니다.
- 대기실에서 연결이 끊기거나 새로고침하는 경우 재시도와 순번을 어떻게 처리할까요?
- 동시 사용자 5,000명을 위한 피크 요청량과 수강신청 진행 시간은 어느 정도인가요?

### 데이터 정합성 참고

- 학생의 학기별 동일 과목 중복 신청과 동일 분반 대기 신청을 데이터 계층에서 막습니다.
- 트랜잭션과 동시성에 안전한 좌석 배정 방식을 사용합니다. 화면에 표시된 잔여 좌석 수만으로 신청을 허용하지 않습니다.
- 멱등성 키와 영속 작업 상태를 사용해 신청과 대기실 작업을 안전하게 재시도할 수 있도록 합니다.
- 대기 순서 정보와 실제 수강 신청 정보를 분리해서 관리합니다.
- 만료되지 않은 대기 등록 제안의 좌석 확보분도 분반 정원 계산에 포함합니다.
