import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Download, Eye, FolderLock, GraduationCap, Loader2, Lock, MessageSquareText, ShieldCheck, Trash2 } from 'lucide-react'
import api, { describeError, getBlobErrorMessage } from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import useConfirm from '../hooks/useConfirm'
import { ROLE_ADMIN, ROLE_STUDENT } from '../constants/roles'
import { VISIBILITY_LABELS } from '../constants/documents'
import Navbar from '../components/Navbar'
import DocumentUpload from '../components/DocumentUpload'
import ErrorState from '../components/ErrorState'
import useDocumentStatus from '../hooks/useDocumentStatus'
import { isClassifying, withLiveStatus } from '../utils/documentStatus'
import DocumentAiBadge from '../components/DocumentAiBadge'
import PiiBadge from '../components/PiiBadge'
import OcrBadge from '../components/OcrBadge'
import ReclassifyButton from '../components/ReclassifyButton'
import { canDeleteDocument, formatSize, getFileFormat } from '../utils/fileTypes'
import { formatDateTime } from '../utils/dates'
import { isOriginalRestricted } from '../utils/privacy'

const PAGE_SIZE = 20

export default function DocumentVaultPage() {
  const { role } = useAuth()
  const { notifyError, showSuccess } = useToast()
  const [confirm, confirmDialog] = useConfirm()
  const isStudent = role === ROLE_STUDENT
  const isAdmin = role === ROLE_ADMIN

  const [page, setPage] = useState(null) // Last loaded page; its content holds all documents loaded so far.
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)

  const fetchPage = useCallback((number) => api.get('/documents', { params: { page: number, size: PAGE_SIZE } })
    .then(({ data }) => data), [])

  // A row changed in place (reclassify), without reloading the list.
  const updateDoc = useCallback((id, changes) => setPage((current) => current && ({
    ...current,
    content: current.content.map((doc) => (doc.id === id ? { ...doc, ...changes } : doc)),
  })), [])

  const [error, setError] = useState('')
  // Rows the AI is still classifying are polled (one request for all of them) and shown live.
  const pendingDocs = useMemo(() => (page?.content ?? []).filter(isClassifying), [page])
  const liveStatuses = useDocumentStatus(pendingDocs, { deadlines: false })

  const reload = useCallback(() => fetchPage(0)
    .then((data) => {
      setPage(data)
      setError('')
    })
    .catch((err) => setError(describeError(err).message))
    .finally(() => setLoading(false)), [fetchPage])

  const retry = () => {
    setError('')
    setLoading(true)
    reload()
  }

  useEffect(() => {
    reload()
  }, [reload])

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

  const handleDownload = async (doc) => {
    setBusyId(doc.id)
    try {
      const { data } = await api.get(`/documents/${doc.id}/download`, { responseType: 'blob', timeout: 0 })
      const url = URL.createObjectURL(data)
      const link = document.createElement('a')
      link.href = url
      link.download = doc.originalFileName
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      await getBlobErrorMessage(err) // Parses the Blob error body in place, so notifyError sees the JSON message.
      notifyError(err)
    } finally {
      setBusyId(null)
    }
  }

  const handleDelete = async (doc) => {
    const confirmed = await confirm({
      title: 'Удалить документ?',
      message: `«${doc.originalFileName}» будет удалён вместе с найденными в нём заданиями и задачами студентов. Это действие нельзя отменить.`,
    })
    if (!confirmed) return
    setBusyId(doc.id)
    try {
      await api.delete(`/documents/${doc.id}`)
      await reload()
      showSuccess(`Документ «${doc.originalFileName}» удалён`)
    } catch (err) {
      notifyError(err)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="min-h-screen bg-slate-50/50 dark:bg-slate-950">
      <Navbar />
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-100 dark:bg-indigo-500/20 text-indigo-700 dark:text-indigo-300">
            <FolderLock className="h-5 w-5" />
          </span>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Хранилище документов</h1>
            <p className="text-sm text-slate-500 dark:text-slate-400">
              {isStudent
                ? 'Материалы курсов, на которые вы записаны'
                : isAdmin ? 'Все документы системы' : 'Документы курсов, которые вы ведёте'}
            </p>
          </div>
        </div>

        {!isStudent && (
          <div className="mt-6">
            <DocumentUpload onUploaded={reload} />
          </div>
        )}

        <div className="mt-4 flex items-center gap-3 rounded-xl border border-emerald-100 dark:border-emerald-500/30 bg-emerald-50/70 dark:bg-emerald-500/10 px-4 py-3 text-sm text-emerald-800 dark:text-emerald-200">
          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-white dark:bg-slate-900 text-emerald-600 dark:text-emerald-400 shadow-sm">
            <ShieldCheck className="h-4 w-4" />
          </span>
          <span className="font-medium">
            {isStudent
              ? 'Вы видите только документы своих курсов. Загружает и удаляет документы преподаватель курса.'
              : 'Студенты видят документы только тех курсов, на которые записаны; заметки «Только преподаватель курса» им недоступны.'}
          </span>
        </div>

        <section className="mt-6 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
          <div className="flex items-center justify-between gap-3 border-b border-slate-200 dark:border-slate-800 px-5 py-4">
            <h2 className="text-base font-semibold text-slate-900 dark:text-white">Документы</h2>
            {page && (
              <span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2.5 py-1 text-xs font-semibold text-slate-600 dark:text-slate-300">{page.totalElements}</span>
            )}
          </div>
          {error ? (
            <ErrorState title="Не удалось загрузить документы" message={error} onRetry={retry} />
          ) : !page && loading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500 dark:text-slate-400">
              <Loader2 className="h-4 w-4 animate-spin" /> Загрузка списка…
            </div>
          ) : page.content.length === 0 ? (
            <div className="flex flex-col items-center py-16 text-center">
              <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 dark:bg-indigo-500/10 text-indigo-400">
                <Lock className="h-7 w-7" />
              </span>
              <p className="mt-3 text-sm font-medium text-slate-700 dark:text-slate-200">Документов пока нет</p>
              <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                {isStudent ? 'Запишитесь на курс, чтобы увидеть его материалы.' : 'Загрузите первый файл в один из своих курсов.'}
              </p>
            </div>
          ) : (
            <>
              {/* Below md: cards instead of the wide table. */}
              <ul className="divide-y divide-slate-100 dark:divide-slate-800 md:hidden">
                {page.content.map((doc) => (
                  <DocumentCard
                    key={doc.id}
                    doc={withLiveStatus(doc, liveStatuses[doc.id])}
                    busy={busyId === doc.id}
                    onDownload={handleDownload}
                    onDelete={handleDelete}
                    onUpdated={updateDoc}
                  />
                ))}
              </ul>
              <div className="hidden overflow-x-auto md:block">
                <table className="w-full min-w-[960px] text-left text-sm">
                  <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400">
                    <tr>
                      <th className="px-4 py-3 font-medium">Документ</th>
                      <th className="px-4 py-3 font-medium">Размер</th>
                      <th className="px-4 py-3 font-medium">Дата загрузки</th>
                      <th className="px-4 py-3 font-medium">Владелец</th>
                      <th className="px-4 py-3 font-medium">Видимость</th>
                      <th className="px-4 py-3 text-right font-medium">Действия</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {page.content.map((doc) => (
                      <DocumentRow
                        key={doc.id}
                        doc={withLiveStatus(doc, liveStatuses[doc.id])}
                        busy={busyId === doc.id}
                        onDownload={handleDownload}
                        onDelete={handleDelete}
                        onUpdated={updateDoc}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
              {!page.last && (
                <button
                  type="button"
                  onClick={loadMore}
                  disabled={loading}
                  className="flex w-full items-center justify-center gap-2 border-t border-slate-100 dark:border-slate-800 py-3 text-sm font-medium text-indigo-600 dark:text-indigo-400 transition hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60"
                >
                  {loading && <Loader2 className="h-4 w-4 animate-spin" />} Показать ещё
                </button>
              )}
            </>
          )}
        </section>
      </main>
      {confirmDialog}
    </div>
  )
}

const actionClass = 'flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-all duration-200 disabled:opacity-60'

function DocumentRow({ doc, busy, onDownload, onDelete, onUpdated }) {
  const format = getFileFormat(doc)
  const FormatIcon = format.icon
  return (
    <tr className="transition-colors duration-200 hover:bg-indigo-50/40 dark:hover:bg-indigo-500/10">
      <td className="max-w-[300px] px-4 py-3">
        <Link to={`/documents/${doc.id}`} className="group flex max-w-full items-center gap-3 rounded-lg focus:outline-none focus-visible:ring-4 focus-visible:ring-indigo-100 dark:focus-visible:ring-indigo-500/30">
          <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${format.tile}`}>
            <FormatIcon className="h-4 w-4" />
          </span>
          <span className="min-w-0">
            <span className="block truncate font-medium text-slate-900 dark:text-white group-hover:text-indigo-700 dark:group-hover:text-indigo-300 group-hover:underline" title={doc.originalFileName}>
              {doc.originalFileName}
            </span>
            <span className="mt-1 flex flex-wrap items-center gap-1">
              <span className={`rounded px-1.5 py-0.5 text-[10px] font-bold ring-1 ring-inset ${format.badge}`}>{format.label}</span>
              <DocumentAiBadge doc={doc} />
              <PiiBadge doc={doc} />
              <OcrBadge doc={doc} />
              {doc.courseCode && (
                <span className="inline-flex items-center gap-0.5 rounded bg-indigo-50 dark:bg-indigo-500/10 px-1.5 py-0.5 text-[10px] font-semibold text-indigo-700 dark:text-indigo-300">
                  <GraduationCap className="h-2.5 w-2.5" /> {doc.courseCode}
                </span>
              )}
            </span>
          </span>
        </Link>
      </td>
      <td className="whitespace-nowrap px-4 py-3 text-slate-600 dark:text-slate-300">{formatSize(doc.fileSize)}</td>
      <td className="whitespace-nowrap px-4 py-3 text-slate-600 dark:text-slate-300">{formatDateTime(doc.uploadedAt)}</td>
      <td className="max-w-[200px] px-4 py-3">
        <span className="block truncate text-slate-700 dark:text-slate-200" title={doc.ownerFullName}>{doc.ownerEmail}</span>
        {doc.isOwner && <span className="mt-0.5 block text-xs text-indigo-600 dark:text-indigo-400">Вы</span>}
      </td>
      <td className="whitespace-nowrap px-4 py-3">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 dark:bg-emerald-500/10 px-2.5 py-1 text-xs font-semibold text-emerald-700 dark:text-emerald-300 ring-1 ring-inset ring-emerald-200 dark:ring-emerald-500/30">
          <Lock className="h-3 w-3" />
          {VISIBILITY_LABELS[doc.visibility] ?? doc.visibility}
        </span>
      </td>
      <td className="whitespace-nowrap px-4 py-3">
        <div className="flex justify-end gap-2">
          <Link to={`/documents/${doc.id}`} className={`${actionClass} border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:border-indigo-200 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 hover:text-indigo-700`}>
            <Eye className="h-3.5 w-3.5" /> Открыть
          </Link>
          {doc.hasExtractedText && (
            <Link to={`/documents/${doc.id}?tab=chat`} className={`${actionClass} border-violet-200 dark:border-violet-500/30 text-violet-700 dark:text-violet-300 hover:bg-violet-50 dark:hover:bg-violet-500/10`}>
              <MessageSquareText className="h-3.5 w-3.5" /> Чат с ИИ
            </Link>
          )}
          {!isOriginalRestricted(doc) && (
            <button type="button" onClick={() => onDownload(doc)} disabled={busy} className={`${actionClass} border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-800`}>
              {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Download className="h-3.5 w-3.5" />} Скачать
            </button>
          )}
          {canDeleteDocument(doc) && (
            <ReclassifyButton doc={doc} onUpdated={(changes) => onUpdated(doc.id, changes)} />
          )}
          {canDeleteDocument(doc) && (
            <button type="button" onClick={() => onDelete(doc)} disabled={busy} className={`${actionClass} border-red-200 dark:border-red-500/30 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10`}>
              <Trash2 className="h-3.5 w-3.5" /> Удалить
            </button>
          )}
        </div>
      </td>
    </tr>
  )
}

const iconActionClass = 'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border transition-all duration-200 disabled:opacity-60'

function DocumentCard({ doc, busy, onDownload, onDelete, onUpdated }) {
  const format = getFileFormat(doc)
  const FormatIcon = format.icon
  return (
    <li className="px-4 py-3">
      <Link to={`/documents/${doc.id}`} className="group flex min-w-0 items-start gap-3 rounded-lg focus:outline-none focus-visible:ring-4 focus-visible:ring-indigo-100 dark:focus-visible:ring-indigo-500/30">
        <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${format.tile}`}>
          <FormatIcon className="h-4 w-4" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium text-slate-900 dark:text-white group-hover:text-indigo-700 dark:group-hover:text-indigo-300" title={doc.originalFileName}>
            {doc.originalFileName}
          </span>
          <span className="mt-1 flex flex-wrap items-center gap-1">
            <span className={`rounded px-1.5 py-0.5 text-[10px] font-bold ring-1 ring-inset ${format.badge}`}>{format.label}</span>
            <DocumentAiBadge doc={doc} />
            <PiiBadge doc={doc} />
            <OcrBadge doc={doc} />
            {doc.courseCode && (
              <span className="inline-flex items-center gap-0.5 rounded bg-indigo-50 dark:bg-indigo-500/10 px-1.5 py-0.5 text-[10px] font-semibold text-indigo-700 dark:text-indigo-300">
                <GraduationCap className="h-2.5 w-2.5" /> {doc.courseCode}
              </span>
            )}
          </span>
          <span className="mt-1 block truncate text-xs text-slate-600 dark:text-slate-300">
            {formatDateTime(doc.uploadedAt)} · {formatSize(doc.fileSize)}
          </span>
        </span>
      </Link>
      <div className="mt-2 flex flex-wrap justify-end gap-2">
        {doc.hasExtractedText && (
          <Link to={`/documents/${doc.id}?tab=chat`} aria-label="Чат с ИИ" title="Чат с ИИ" className={`${iconActionClass} border-violet-200 dark:border-violet-500/30 text-violet-700 dark:text-violet-300 hover:bg-violet-50 dark:hover:bg-violet-500/10`}>
            <MessageSquareText className="h-4 w-4" />
          </Link>
        )}
        {!isOriginalRestricted(doc) && (
          <button type="button" onClick={() => onDownload(doc)} disabled={busy} aria-label="Скачать" title="Скачать" className={`${iconActionClass} border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-800`}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
          </button>
        )}
        {canDeleteDocument(doc) && (
          <ReclassifyButton doc={doc} onUpdated={(changes) => onUpdated(doc.id, changes)} compact />
        )}
        {canDeleteDocument(doc) && (
          <button type="button" onClick={() => onDelete(doc)} disabled={busy} aria-label="Удалить" title="Удалить" className={`${iconActionClass} border-red-200 dark:border-red-500/30 text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10`}>
            <Trash2 className="h-4 w-4" />
          </button>
        )}
      </div>
    </li>
  )
}
