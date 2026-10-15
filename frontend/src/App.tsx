import { useCallback, useEffect, useMemo, useState } from 'react'

type Meeting = { day: string; startsAt: string; endsAt: string }
type Section = {
  id: string
  courseCode: string
  title: string
  credits: number
  sectionCode: string
  instructor: string
  capacity: number
  enrolled: number
  enrolledByMe: boolean
  remainingSeats: number
  meetings: Meeting[]
}
type ApiError = { code: string; message: string }

const termId = '2026-FALL'
const dayNames: Record<string, string> = {
  MONDAY: '월', TUESDAY: '화', WEDNESDAY: '수', THURSDAY: '목',
  FRIDAY: '금', SATURDAY: '토', SUNDAY: '일',
}

async function readError(response: Response) {
  const body = (await response.json().catch(() => null)) as ApiError | null
  return body?.message ?? '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export default function App() {
  const [studentNumber, setStudentNumber] = useState('20240001')
  const [sections, setSections] = useState<Section[]>([])
  const [query, setQuery] = useState('')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const loadSections = useCallback(async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams({ studentNumber })
      const response = await fetch(`/api/v1/terms/${termId}/sections?${params}`)
      if (!response.ok) throw new Error(await readError(response))
      setSections((await response.json()) as Section[])
      setError('')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '강좌를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [studentNumber])

  useEffect(() => { void loadSections() }, [loadSections])

  const visibleSections = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return sections
    return sections.filter((section) =>
      `${section.courseCode} ${section.title} ${section.instructor}`.toLowerCase().includes(normalized),
    )
  }, [query, sections])

  const registeredCount = sections.filter((section) => section.enrolledByMe).length
  const credits = sections.reduce((total, section) => total + (section.enrolledByMe ? section.credits : 0), 0)

  async function changeEnrollment(section: Section) {
    setBusyId(section.id)
    setNotice('')
    setError('')
    try {
      const response = section.enrolledByMe
        ? await fetch(`/api/v1/sections/${section.id}/enrollments/me?studentNumber=${encodeURIComponent(studentNumber)}`, { method: 'DELETE' })
        : await fetch(`/api/v1/sections/${section.id}/enrollments`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
            body: JSON.stringify({ studentNumber }),
          })
      if (!response.ok) throw new Error(await readError(response))
      setNotice(section.enrolledByMe ? `${section.courseCode} 신청을 취소했어요.` : `${section.courseCode} 신청이 완료됐어요.`)
      await loadSections()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '요청을 처리하지 못했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="CourseFlow 홈"><span className="brand-mark">C</span><span>courseflow</span></a>
        <nav><a className="nav-active" href="#courses">강좌 찾기</a><a href="#my-status">내 신청 현황</a><span className="term-pill">2026학년도 2학기</span></nav>
        <div className="profile"><span className="profile-dot">M</span><span>{studentNumber}</span></div>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero-copy">
            <span className="eyebrow"><span className="live-dot" /> 수강신청 진행 중</span>
            <h1>이번 학기,<br /><em>나에게 맞는</em> 수업을 담아보세요.</h1>
            <p>관심 있는 강좌를 찾아 시간표를 채워보세요.<br />신청 전 선수 과목과 시간 충돌을 자동으로 확인해요.</p>
            <a className="hero-link" href="#courses">강좌 둘러보기 <span>↘</span></a>
          </div>
          <div className="hero-art" aria-hidden="true">
            <div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" />
            <div className="art-card"><div className="art-card-top"><span>MY SEMESTER</span><span>2026.09 — 12</span></div><div className="art-lines"><i /><i /><i /><i /></div><div className="art-stamp">이번 학기<br /><b>잘 부탁해!</b> ✳</div></div>
            <span className="art-spark spark-a">✳</span><span className="art-spark spark-b">✳</span>
          </div>
        </section>

        <section className="content" id="courses">
          <div className="section-heading">
            <div><span className="eyebrow muted">COURSE CATALOG</span><h2>강좌 둘러보기 <span className="count">{sections.length}</span></h2></div>
            <label className="student-field">학번 <input value={studentNumber} onChange={(event) => setStudentNumber(event.target.value)} aria-label="학번" /></label>
          </div>
          <div className="toolbar"><label className="search"><span>⌕</span><input placeholder="과목명, 과목 코드, 교수명으로 검색" value={query} onChange={(event) => setQuery(event.target.value)} /><kbd>⌘ K</kbd></label><button className="filter-button" onClick={() => void loadSections()}>↻ <span>새로고침</span></button><span className="sort-label">전체 강좌 <b>⌄</b></span></div>

          {(notice || error) && <div className={`notice ${error ? 'notice-error' : ''}`} role="status">{error || notice}</div>}

          <div className="course-list">
            {loading && <div className="empty-state">강좌를 불러오는 중이에요…</div>}
            {!loading && visibleSections.length === 0 && <div className="empty-state">검색 결과가 없어요. 다른 검색어를 입력해 보세요.</div>}
            {visibleSections.map((section, index) => {
              const full = section.remainingSeats === 0
              return <article className="course-row" key={section.id}>
                <div className={`course-index index-${index % 4}`}>{String(index + 1).padStart(2, '0')}</div>
                <div className="course-main"><div className="course-code">{section.courseCode}<span>분반 {section.sectionCode}</span></div><h3>{section.title}</h3><div className="course-meta"><span>◷ {section.meetings.map((meeting) => `${dayNames[meeting.day] ?? meeting.day} ${meeting.startsAt.slice(0, 5)}`).join(' · ')}</span><span>♙ {section.instructor} 교수</span></div></div>
                <div className="course-credit"><strong>{section.credits}</strong><span>학점</span></div>
                <div className="seat-info"><span className={full ? 'seat-full' : ''}>{full ? '정원 마감' : '신청 가능'}</span><div className="seat-track"><i style={{ width: `${Math.min(100, Math.round((section.enrolled / section.capacity) * 100))}%` }} /></div><small>{section.enrolled} / {section.capacity}명</small></div>
                <button className={`enroll-button ${section.enrolledByMe ? 'is-enrolled' : ''}`} disabled={busyId === section.id || (!section.enrolledByMe && full)} onClick={() => void changeEnrollment(section)}>{busyId === section.id ? '처리 중' : section.enrolledByMe ? '신청 취소' : full ? '대기 신청 준비 중' : '신청하기'}<span>{section.enrolledByMe ? '✓' : '↗'}</span></button>
              </article>
            })}
          </div>
        </section>

        <aside className="summary" id="my-status"><div><span className="eyebrow muted">MY ENROLLMENT</span><strong>{registeredCount}<small> 과목 신청</small></strong><span className="summary-divider" /><span className="summary-credits">총 신청 학점 <b>{credits}학점</b></span></div><span className="summary-note">수강신청 현황은 신청 완료 후<br />자동으로 업데이트됩니다.</span><a href="#courses">내 시간표 확인하기 <span>↗</span></a></aside>
      </main>
      <footer><span>© 2026 CourseFlow</span><span>차분하게, 원하는 수업을 찾아보세요.</span><span>도움이 필요하신가요? <b>문의하기 ↗</b></span></footer>
    </div>
  )
}

