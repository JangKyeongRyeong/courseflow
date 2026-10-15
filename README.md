# CourseFlow

AI-assisted course registration project built on the existing Spring Boot 4, Gradle, Java 17, and MySQL starter in this repository. The product goal is to design and measure a system for enrollment peaks, with 5,000+ concurrent users as a target to validate rather than a current capacity claim.

## Current implementation

- React + TypeScript catalog screen in `frontend/`.
- Spring MVC API added to the existing Gradle backend in `backend/`.
- MySQL schema and repeatable demo seed data for students, courses, sections, schedules, enrollments, waitlist entries, and waitlist offers.
- Catalog lookup and transactional enrollment/drop operations.
- Enrollment checks for term window, prerequisites, duplicate course, schedule conflicts, and credit limits.
- Section and student row locks to serialize seat allocation and per-student schedule/credit checks; idempotency keys for enrollment requests.

The virtual waiting room and waitlist-offer lifecycle are not connected to API routes yet. Authentication is not implemented; the demo student number is user supplied and must not be treated as identity outside local development. Load capacity has not been tested.

## Run locally

Use the existing MySQL Compose configuration in `infra/`. It reads passwords from `infra/.env`; keep those values private.

1. Start MySQL from the repository root: `docker compose --env-file infra/.env -f infra/docker-compose.yml up -d mysql`
2. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for the API process. `DB_URL` defaults to `jdbc:mysql://localhost:3306/courseflow?connectionTimeZone=UTC`.
3. Start the API from the repository root: `./backend/gradlew -p backend bootRun` (Windows PowerShell: `.\backend\gradlew.bat -p backend bootRun`).
4. In `frontend/`, run `npm install` and `npm run dev`.
5. Open `http://localhost:5173`. The seeded demo student number is `20240001`.

Spring SQL initialization creates the tables and inserts demo catalog rows when the API starts. Do not point this demo initializer at a database containing valuable data.

## Product decisions

- The first release includes waitlists, schedule-conflict checks, prerequisites, and credit limits.
- A waitlisted student receives an offer with a configurable acceptance deadline. The offered seat is held until acceptance or expiry; expiry moves the offer to the next eligible student.
- A virtual waiting room controls admission during enrollment peaks. Admission does not reserve a seat.
- AI is limited to catalog discovery and schedule suggestions; it must not create, cancel, or reserve enrollments.

See [docs/mvp-domain-model.md](docs/mvp-domain-model.md) for the domain and API draft.

## Next engineering slices

1. Add waitlist join/leave, offer acceptance/expiry, and promotion APIs.
2. Add a Redis-backed waiting room with short-lived admission tokens.
3. Add university authentication and replace the demo student-number input.
4. Add metrics, realistic workload definitions, and load testing before making a 5,000-user capacity claim.
5. Add AI-assisted course discovery after enrollment policies are stable.

## 한국어 안내

CourseFlow는 기존 저장소의 Spring Boot 4, Gradle, Java 17, MySQL 기반 위에 만드는 AI 활용 수강신청 프로젝트입니다. 5천 명 이상 동시 접속은 현재 보장된 처리량이 아니라 설계와 부하 테스트로 검증할 목표입니다.

### 현재 구현된 내용

- `frontend/`에 React + TypeScript 강좌 목록 화면을 추가했습니다.
- 기존 Gradle 백엔드 `backend/`에 Spring MVC API를 추가했습니다.
- 학생, 강좌, 분반, 시간표, 수강 신청, 대기 신청, 등록 제안의 MySQL 스키마와 반복 가능한 데모 데이터를 추가했습니다.
- 강좌 조회와 트랜잭션 기반 신청·취소 흐름을 구현했습니다.
- 신청 시 신청 기간, 선수 과목, 같은 과목 중복, 시간표 충돌, 학점 제한을 확인합니다.
- 좌석 배정과 학생별 시간표·학점 검사를 직렬화하도록 분반과 학생 행을 잠그고, 신청 재시도에 멱등성 키를 사용합니다.

가상 대기실과 대기 등록 제안의 전체 흐름은 아직 API에 연결되지 않았습니다. 인증도 구현 전이라 데모 학번은 사용자가 직접 입력합니다. 로컬 개발 외 환경에서 신원 확인 용도로 사용하면 안 됩니다. 처리량 부하 테스트도 아직 하지 않았습니다.

### 로컬 실행 방법

기존 `infra/`의 MySQL Compose 설정을 사용합니다. 비밀번호는 `infra/.env`에 있으므로 외부에 공개하지 마세요.

1. 저장소 루트에서 MySQL 실행: `docker compose --env-file infra/.env -f infra/docker-compose.yml up -d mysql`
2. API 프로세스에 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 설정합니다. `DB_URL` 기본값은 `jdbc:mysql://localhost:3306/courseflow?connectionTimeZone=UTC`입니다.
3. 저장소 루트에서 API 실행: `./backend/gradlew -p backend bootRun` (Windows PowerShell: `.\backend\gradlew.bat -p backend bootRun`)
4. `frontend/`에서 `npm install` 후 `npm run dev`를 실행합니다.
5. `http://localhost:5173`에 접속합니다. 데모 학번은 `20240001`입니다.

API를 시작할 때 SQL 초기화가 테이블과 데모 강좌 데이터를 준비합니다. 중요한 데이터가 있는 DB를 데모 초기화 설정에 연결하지 마세요.

### 확정된 제품 결정

- 첫 버전에 대기 신청, 시간표 충돌 검사, 선수 과목 검사, 학점 제한을 포함합니다.
- 대기자에게 설정 가능한 수락 기한이 있는 등록 제안을 보냅니다. 수락 또는 만료 전까지 좌석을 확보하고, 만료되면 다음 적격 대기자에게 제안합니다.
- 피크 시간에는 가상 대기실로 입장을 제어합니다. 입장만으로 좌석이 확보되지는 않습니다.
- AI는 강좌 탐색과 시간표 추천에만 사용하며 수강 신청을 생성·취소하거나 좌석을 예약하지 않습니다.

도메인과 API 초안은 [docs/mvp-domain-model.md](docs/mvp-domain-model.md)를 참고하세요.

### 다음 개발 단계

1. 대기 신청 등록·취소, 제안 수락·만료, 다음 대기자 승격 API를 구현합니다.
2. 만료 시간이 짧은 입장 토큰을 사용하는 Redis 기반 가상 대기실을 구현합니다.
3. 대학 인증을 추가하고 데모 학번 입력을 대체합니다.
4. 지표와 실제 부하 조건을 정하고, 5천 명 처리량을 주장하기 전에 부하 테스트를 합니다.
5. 신청 정책이 안정된 뒤 AI 강좌 탐색 기능을 추가합니다.
