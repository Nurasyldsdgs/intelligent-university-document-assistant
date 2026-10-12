import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Check, ChevronRight, Copy, FileText, GraduationCap, Loader2, ShieldAlert, UserPlus, Users } from 'lucide-react'
import api, { describeError } from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { ROLE_STUDENT } from '../constants/roles'
import { CATEGORY_LABELS, VISIBILITY_LABELS } from '../constants/documents'
import Navbar from '../components/Navbar'
import CourseChat from '../components/CourseChat'
import CourseAssignments from '../components/CourseAssignments'
import CourseStudents from '../components/CourseStudents'
import DocumentUpload from '../components/DocumentUpload'
import { dateFormatter, getFileFormat } from '../utils/fileTypes'

const DOCS_PAGE_SIZE = 10

export default function CoursePage() {
  const { id } = useParams()
  const { role } = useAuth()
  const { notifyError, showSuccess } = useToast()
  const [details, setDetails] = useState(null)
  const [error, setError] = useState(null)
  const [enrolling, setEnrolling] = useState(false)
  const [docsVersion, setDocsVersion] = useState(0) // Bumped after an upload to reload the list.

  const loadDetails = useCallback(() => api.get(`/courses/${id}`).then(({ data }) => setDetails(data)), [id])

  useEffect(() => {
    loadDetails().catch((err) => setError(describeError(err)))
  }, [loadDetails])

  const enroll = async () => {
    setEnrolling(true)
    try {
      await api.post(`/courses/${id}/enroll`)
      await loadDetails()
      setError(null)
      setDocsVersion((version) => version + 1)
      showSuccess('Вы записаны на курс')
    } catch (err) {
      notifyError(err)
    } finally {
      setEnrolling(false)
    }
  }

  const course = details?.course
  const isStudent = role === ROLE_STUDENT
  // Only the course's teacher and ADMIN manage its documents; students read them.
  const canManage = Boolean(details?.canManage)
  // The backend answers 403 to anyone who is neither enrolled in the course nor teaches it.
  const notMember = error?.status === 403

  return (
    <div className="min-h-screen bg-slate-50/50 dark:bg-slate-950">
      <Navbar />
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 sm:py-8">
        <Link to="/courses" className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400">
          <ArrowLeft className="h-4 w-4" /> Все курсы
        </Link>

        {error ? (
          <div className="mx-auto mt-16 flex max-w-md flex-col items-center text-center">
            <ShieldAlert className="h-10 w-10 text-slate-400" />
            <h1 className="mt-3 text-lg font-semibold text-slate-900 dark:text-white">
              {error.status === 404 ? 'Курс не найден' : notMember ? 'Нет доступа к курсу' : 'Не удалось открыть курс'}
            </h1>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {notMember
                ? (isStudent
                  ? 'Материалы курса доступны только записанным студентам. Если преподаватель прислал вам эту ссылку, запишитесь на курс.'
                  : 'Курс ведёт другой преподаватель. Вы можете управлять только своими курсами.')
                : error.message}
            </p>
            {notMember && isStudent && (
              <button
                type="button"
                onClick={enroll}
                disabled={enrolling}
                className="mt-4 flex items-center gap-2 rounded-lg bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:opacity-60"
              >
                {enrolling ? <Loader2 className="h-4 w-4 animate-spin" /> : <UserPlus className="h-4 w-4" />} Записаться на курс
              </button>
            )}
          </div>
        ) : !details ? (
          <div className="flex items-center justify-center gap-2 py-24 text-sm text-slate-500 dark:text-slate-400">
            <Loader2 className="h-4 w-4 animate-spin" /> Загрузка курса…
          </div>
        ) : (
          <>
            <section className="mt-4 flex flex-col gap-4 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-sm sm:flex-row sm:items-start">
              <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-indigo-100 dark:bg-indigo-500/20 text-indigo-700 dark:text-indigo-300">
                <GraduationCap className="h-6 w-6" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  {course.code && <span className="rounded-md bg-indigo-50 dark:bg-indigo-500/10 px-1.5 py-0.5 text-xs font-bold text-indigo-700 dark:text-indigo-300">{course.code}</span>}
                  <span className="text-xs text-slate-400">Курс № {course.id}</span>
                  {details.enrolled && <span className="rounded-md bg-emerald-50 dark:bg-emerald-500/10 px-1.5 py-0.5 text-xs font-semibold text-emerald-700 dark:text-emerald-300">Вы записаны</span>}
                </div>
                <h1 className="mt-1 text-xl font-bold text-slate-900 dark:text-white">{course.title}</h1>
                {course.description && <p className="mt-1 whitespace-pre-wrap text-sm text-slate-600 dark:text-slate-300">{course.description}</p>}
                <p className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500 dark:text-slate-400">
                  <span>Преподаватель: {course.teacherFullName || course.teacherEmail || 'не назначен'}</span>
                  <span className="flex items-center gap-1"><Users className="h-3.5 w-3.5" /> Студентов: {details.studentCount}</span>
                </p>
              </div>
              <div className="flex shrink-0 gap-2">
                {canManage && <CopyLinkButton />}
              </div>
            </section>

            <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
              <div className="space-y-4">
                {canManage && <DocumentUpload course={course} onUploaded={() => setDocsVersion((version) => version + 1)} />}
                <CourseDocuments courseId={course.id} version={docsVersion} />
                <CourseAssignments courseId={course.id} canManage={canManage} version={docsVersion} />
                {canManage && <CourseStudents courseId={course.id} onChange={() => loadDetails().catch(() => {})} />}
              </div>
              <div className="lg:sticky lg:top-20 lg:self-start">
                <CourseChat course={course} />
              </div>
            </div>
          </>
        )}
      </main>
    </div>
  )
}

// Teachers share this link with students; for a student who is not enrolled yet it opens the "Записаться" button.
function CopyLinkButton() {
  const [copied, setCopied] = useState(false)
  useEffect(() => {
    if (!copied) return undefined
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])

  return (
    <button
      type="button"
      onClick={() => navigator.clipboard?.writeText(window.location.href).then(() => setCopied(true)).catch(() => {})}
      className="flex items-center gap-2 rounded-lg border border-slate-300 dark:border-slate-700 px-3.5 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800"
      title="Ссылка для записи студентов"
    >
      {copied ? <Check className="h-4 w-4 text-emerald-600" /> : <Copy className="h-4 w-4" />}
      {copied ? 'Скопировано' : 'Ссылка для студентов'}
    </button>
  )
}

function CourseDocuments({ courseId, version }) {
  const { notifyError } = useToast()
  const [page, setPage] = useState(null)
  const [loading, setLoading] = useState(true)

  const fetchPage = useCallback((number) => api.get('/documents', {
    params: { courseId, page: number, size: DOCS_PAGE_SIZE },
  }).then(({ data }) => data), [courseId])

  useEffect(() => {
    let cancelled = false
    fetchPage(0)
      .then((data) => !cancelled && setPage(data))
      .catch((err) => !cancelled && notifyError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [fetchPage, notifyError, version])

  const loadMore = async () => {
    setLoading(true)
    try {
      const next = await fetchPage(page.page + 1)
      setPage({ ...next, content: [...page.content, ...next.content] })
    } catch (err) {
      notifyError(err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
      <h2 className="flex items-center gap-2 border-b border-slate-200 dark:border-slate-800 px-5 py-3.5 text-sm font-semibold text-slate-900 dark:text-white">
        <FileText className="h-4 w-4 text-indigo-600" /> Документы курса
        {page && <span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:text-slate-300">{page.totalElements}</span>}
      </h2>
      {!page && loading ? (
        <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Загрузка…</div>
      ) : page?.content.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-slate-500 dark:text-slate-400">Доступных вам документов в курсе пока нет.</p>
      ) : page && (
        <>
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {page.content.map((doc) => {
              const format = getFileFormat(doc)
              const FormatIcon = format.icon
              return (
                <li key={doc.id}>
                  <Link to={`/documents/${doc.id}`} className="group flex items-center gap-3 px-5 py-3 transition hover:bg-indigo-50/40 dark:hover:bg-indigo-500/10">
                    <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${format.tile}`}><FormatIcon className="h-4 w-4" /></span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-slate-900 dark:text-white group-hover:text-indigo-700 dark:group-hover:text-indigo-300">{doc.originalFileName}</span>
                      <span className="block truncate text-xs text-slate-500 dark:text-slate-400">
                        {CATEGORY_LABELS[doc.category]} · {VISIBILITY_LABELS[doc.visibility]} · {dateFormatter.format(new Date(doc.uploadedAt))}
                      </span>
                    </span>
                    <ChevronRight className="h-4 w-4 shrink-0 text-slate-400 group-hover:text-indigo-600" />
                  </Link>
                </li>
              )
            })}
          </ul>
          {!page.last && (
            <button type="button" onClick={loadMore} disabled={loading} className="flex w-full items-center justify-center gap-2 border-t border-slate-100 dark:border-slate-800 py-2.5 text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60">
              {loading && <Loader2 className="h-4 w-4 animate-spin" />} Показать ещё
            </button>
          )}
        </>
      )}
    </section>
  )
}
