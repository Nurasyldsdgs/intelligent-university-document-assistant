import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Check, ChevronRight, Copy, FileText, GraduationCap, Loader2, RefreshCw, ShieldAlert, Users } from 'lucide-react'
import api, { describeError } from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { ROLE_ADMIN, ROLE_STUDENT } from '../constants/roles'
import { VISIBILITY_LABELS } from '../constants/documents'
import Navbar from '../components/Navbar'
import ConfirmDialog from '../components/ConfirmDialog'
import ErrorState from '../components/ErrorState'
import CourseChat from '../components/CourseChat'
import CourseAssignments from '../components/CourseAssignments'
import CourseDrafts from '../components/CourseDrafts'
import CourseStudents from '../components/CourseStudents'
import DocumentUpload from '../components/DocumentUpload'
import DocumentAiBadge from '../components/DocumentAiBadge'
import PiiBadge from '../components/PiiBadge'
import OcrBadge from '../components/OcrBadge'
import ReclassifyButton from '../components/ReclassifyButton'
import { canDeleteDocument, getFileFormat } from '../utils/fileTypes'
import { formatDateTime } from '../utils/dates'
import useDocumentStatus from '../hooks/useDocumentStatus'
import { isClassifying, withLiveStatus } from '../utils/documentStatus'

const DOCS_PAGE_SIZE = 10

export default function CoursePage() {
  const { id } = useParams()
  const { role } = useAuth()
  const [details, setDetails] = useState(null)
  const [error, setError] = useState(null)
  const [docsVersion, setDocsVersion] = useState(0) // Bumped after an upload to reload the list.
  // Bumped after drafts are published, so the course's assignments list shows them.
  const [assignmentsVersion, setAssignmentsVersion] = useState(0)

  const loadDetails = useCallback(() => api.get(`/courses/${id}`).then(({ data }) => setDetails(data)), [id])

  useEffect(() => {
    loadDetails().catch((err) => setError(describeError(err)))
  }, [loadDetails])

  const retryDetails = () => {
    setError(null)
    loadDetails().catch((err) => setError(describeError(err)))
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
                  ? 'Материалы курса доступны только записанным студентам. Чтобы записаться, откройте ссылку-приглашение от преподавателя.'
                  : 'Курс ведёт другой преподаватель. Вы можете управлять только своими курсами.')
                : error.message}
            </p>
            {error.status !== 403 && error.status !== 404 && (
              <button
                type="button"
                onClick={retryDetails}
                className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-indigo-700"
              >
                <RefreshCw className="h-4 w-4" /> Повторить
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
                  {role === ROLE_ADMIN && <span className="text-xs text-slate-500 dark:text-slate-400">Курс № {course.id}</span>}
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
                {canManage && <InviteLinkControls courseId={course.id} />}
              </div>
            </section>

            <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
              <div className="space-y-4">
                {canManage && <DocumentUpload course={course} onUploaded={() => setDocsVersion((version) => version + 1)} />}
                <CourseDocuments courseId={course.id} version={docsVersion} />
                {canManage && (
                  <CourseDrafts courseId={course.id} version={docsVersion} onPublished={() => setAssignmentsVersion((version) => version + 1)} />
                )}
                <CourseAssignments courseId={course.id} canManage={canManage} version={docsVersion + assignmentsVersion} />
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

/**
 * The invitation link /join/{code} that teachers share with students - the only way to join a course. "Новая ссылка"
 * replaces the code, so the old link stops working (e.g. after it leaked).
 */
function InviteLinkControls({ courseId }) {
  const { notifyError, showSuccess } = useToast()
  const [code, setCode] = useState(null)
  const [copied, setCopied] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [regenerating, setRegenerating] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    api.get(`/courses/${courseId}/invite`)
      .then(({ data }) => {
        if (cancelled) return
        setCode(data.inviteCode)
        setLoadError('')
      })
      .catch((err) => !cancelled && setLoadError(describeError(err).message))
    return () => {
      cancelled = true
    }
  }, [courseId, attempt])

  useEffect(() => {
    if (!copied) return undefined
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])

  const link = code ? `${window.location.origin}/join/${code}` : ''

  const copy = () => {
    if (!navigator.clipboard) {
      window.prompt('Скопируйте ссылку для студентов:', link)
      return
    }
    navigator.clipboard.writeText(link).then(() => setCopied(true)).catch(() => window.prompt('Скопируйте ссылку для студентов:', link))
  }

  const regenerate = async () => {
    setRegenerating(true)
    try {
      const { data } = await api.post(`/courses/${courseId}/invite/regenerate`)
      setCode(data.inviteCode)
      setConfirming(false)
      showSuccess('Создана новая ссылка. Старая больше не действует.')
    } catch (err) {
      notifyError(err)
    } finally {
      setRegenerating(false)
    }
  }

  const buttonClass = 'flex items-center gap-2 rounded-lg border border-slate-300 dark:border-slate-700 px-3.5 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60'
  if (loadError && !code) {
    return (
      <ErrorState
        compact
        message="Не удалось загрузить ссылку"
        onRetry={() => {
          setLoadError('')
          setAttempt((value) => value + 1)
        }}
        className="max-w-xs"
      />
    )
  }
  return (
    <>
      <button type="button" onClick={copy} disabled={!code} className={buttonClass} title={link || 'Загрузка ссылки…'}>
        {copied ? <Check className="h-4 w-4 text-emerald-600" /> : <Copy className="h-4 w-4" />}
        {copied ? 'Скопировано' : 'Ссылка для студентов'}
      </button>
      <button type="button" onClick={() => setConfirming(true)} disabled={!code} className={buttonClass} title="Сгенерировать новую ссылку" aria-label="Новая ссылка для студентов">
        <RefreshCw className="h-4 w-4" /> <span className="hidden sm:inline">Новая ссылка</span>
      </button>
      {confirming && (
        <ConfirmDialog
          title="Сгенерировать новую ссылку?"
          confirmLabel="Сгенерировать"
          loading={regenerating}
          onConfirm={regenerate}
          onCancel={() => setConfirming(false)}
        >
          Текущая ссылка-приглашение сразу перестанет работать. Уже записанные студенты останутся на курсе;
          новым студентам нужно будет отправить новую ссылку.
        </ConfirmDialog>
      )}
    </>
  )
}

function CourseDocuments({ courseId, version }) {
  const { notifyError } = useToast()
  const [page, setPage] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  const fetchPage = useCallback((number) => api.get('/documents', {
    params: { courseId, page: number, size: DOCS_PAGE_SIZE },
  }).then(({ data }) => data), [courseId])

  useEffect(() => {
    let cancelled = false
    fetchPage(0)
      .then((data) => {
        if (cancelled) return
        setPage(data)
        setError('')
      })
      .catch((err) => !cancelled && setError(describeError(err).message))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [fetchPage, version, attempt])

  const retry = () => {
    setError('')
    setLoading(true)
    setAttempt((value) => value + 1)
  }

  const updateDoc = useCallback((id, changes) => setPage((current) => current && ({
    ...current,
    content: current.content.map((doc) => (doc.id === id ? { ...doc, ...changes } : doc)),
  })), [])

  // Rows the AI is still classifying are polled (one request for all of them) and shown live.
  const pendingDocs = useMemo(() => (page?.content ?? []).filter(isClassifying), [page])
  const liveStatuses = useDocumentStatus(pendingDocs, { deadlines: false })

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
      {error ? (
        <ErrorState title="Не удалось загрузить документы курса" message={error} onRetry={retry} />
      ) : !page && loading ? (
        <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Загрузка…</div>
      ) : page?.content.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-slate-500 dark:text-slate-400">Доступных вам документов в курсе пока нет.</p>
      ) : page && (
        <>
          <ul className="divide-y divide-slate-100 dark:divide-slate-800">
            {page.content.map((row) => {
              const doc = withLiveStatus(row, liveStatuses[row.id])
              const format = getFileFormat(doc)
              const FormatIcon = format.icon
              return (
                <li key={doc.id} className="flex items-center gap-2 pr-3 transition hover:bg-indigo-50/40 dark:hover:bg-indigo-500/10">
                  <Link to={`/documents/${doc.id}`} state={{ from: `/courses/${courseId}` }} className="group flex min-w-0 flex-1 items-center gap-3 py-3 pl-4 sm:pl-5">
                    <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${format.tile}`}><FormatIcon className="h-4 w-4" /></span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-slate-900 dark:text-white group-hover:text-indigo-700 dark:group-hover:text-indigo-300">{doc.originalFileName}</span>
                      <span className="mt-0.5 flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-slate-500 dark:text-slate-400">
                        <DocumentAiBadge doc={doc} />
                        <PiiBadge doc={doc} />
                        <OcrBadge doc={doc} />
                        <span className="truncate">{VISIBILITY_LABELS[doc.visibility]} · {formatDateTime(doc.uploadedAt)}</span>
                      </span>
                    </span>
                    <ChevronRight className="h-4 w-4 shrink-0 text-slate-400 group-hover:text-indigo-600" />
                  </Link>
                  {canDeleteDocument(doc) && (
                    <ReclassifyButton doc={doc} onUpdated={(changes) => updateDoc(doc.id, changes)} compact />
                  )}
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
