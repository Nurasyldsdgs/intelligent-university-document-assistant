import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ArrowLeft, Download, Eye, GraduationCap, Loader2, LockKeyhole, MessageSquareText, ShieldAlert, Sparkles, Tag, Trash2 } from 'lucide-react'
import api, { describeError, getBlobErrorMessage } from '../api/axios'
import { useToast } from '../context/ToastContext'
import Navbar from '../components/Navbar'
import DocumentPreview from '../components/DocumentPreview'
import AnalysisPanel from '../components/AnalysisPanel'
import DocumentChat from '../components/DocumentChat'
import { CATEGORY_LABELS, VISIBILITY_LABELS } from '../constants/documents'
import { canDeleteDocument, dateFormatter, formatSize, getFileFormat } from '../utils/fileTypes'

const TABS = [
  { id: 'preview', label: 'Просмотр', icon: Eye },
  { id: 'analysis', label: 'Анализ ИИ и задачи', icon: Sparkles },
  { id: 'chat', label: 'Чат с ИИ', icon: MessageSquareText },
]

export default function DocumentPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { notifyError, showSuccess } = useToast()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = TABS.some((item) => item.id === searchParams.get('tab')) ? searchParams.get('tab') : 'preview'

  const [doc, setDoc] = useState(null)
  const [error, setError] = useState(null) // { status, message }
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    api.get(`/documents/${id}`)
      .then(({ data }) => !cancelled && setDoc(data))
      .catch((err) => !cancelled && setError(describeError(err)))
    return () => {
      cancelled = true
    }
  }, [id])

  const download = async () => {
    setBusy(true)
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
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!window.confirm(`Удалить «${doc.originalFileName}»? Вместе с ним удалятся его задачи. Это действие нельзя отменить.`)) return
    setBusy(true)
    try {
      await api.delete(`/documents/${doc.id}`)
      showSuccess(`Документ «${doc.originalFileName}» удалён`)
      navigate('/documents', { replace: true })
    } catch (err) {
      notifyError(err)
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-50/50 dark:bg-slate-950">
      <Navbar />
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 sm:py-8">
        <Link to="/documents" className="inline-flex items-center gap-1.5 text-sm font-medium text-slate-500 dark:text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400">
          <ArrowLeft className="h-4 w-4" /> Хранилище документов
        </Link>

        {error ? (
          <ErrorState error={error} />
        ) : !doc ? (
          <div className="flex items-center justify-center gap-2 py-24 text-sm text-slate-500 dark:text-slate-400">
            <Loader2 className="h-4 w-4 animate-spin" /> Загрузка документа…
          </div>
        ) : (
          <>
            <DocumentHeader doc={doc} busy={busy} onDownload={download} onDelete={remove} />

            <div className="mt-6 flex gap-1 overflow-x-auto rounded-xl bg-slate-100 dark:bg-slate-800/70 p-1" role="tablist">
              {TABS.map(({ id: tabId, label, icon: Icon }) => (
                <button
                  key={tabId}
                  type="button"
                  role="tab"
                  aria-selected={tab === tabId}
                  onClick={() => setSearchParams(tabId === 'preview' ? {} : { tab: tabId }, { replace: true })}
                  className={`flex shrink-0 items-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition ${
                    tab === tabId
                      ? 'bg-white dark:bg-slate-900 text-indigo-700 dark:text-indigo-300 shadow-sm'
                      : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white'
                  }`}
                >
                  <Icon className="h-4 w-4" /> {label}
                </button>
              ))}
            </div>

            <div className="mt-5" role="tabpanel">
              {tab === 'preview' && <DocumentPreview doc={doc} />}
              {tab === 'analysis' && <AnalysisPanel doc={doc} />}
              {tab === 'chat' && <DocumentChat doc={doc} />}
            </div>
          </>
        )}
      </main>
    </div>
  )
}

function DocumentHeader({ doc, busy, onDownload, onDelete }) {
  const format = getFileFormat(doc)
  const FormatIcon = format.icon
  const badge = 'inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset'

  return (
    <section className="mt-4 flex flex-col gap-4 rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-sm sm:flex-row sm:items-start">
      <span className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-xl ${format.tile}`}>
        <FormatIcon className="h-6 w-6" />
      </span>
      <div className="min-w-0 flex-1">
        <h1 className="break-words text-xl font-bold text-slate-900 dark:text-white">{doc.originalFileName}</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          {formatSize(doc.fileSize)} · {dateFormatter.format(new Date(doc.uploadedAt))} · {doc.ownerFullName || doc.ownerEmail}
          {doc.isOwner && <span className="text-indigo-600 dark:text-indigo-400"> (вы)</span>}
        </p>
        <div className="mt-3 flex flex-wrap gap-1.5">
          <span className={`${badge} font-bold ${format.badge}`}>{format.label}</span>
          <span className={`${badge} bg-slate-50 dark:bg-slate-800 text-slate-700 dark:text-slate-200 ring-slate-200 dark:ring-slate-700`}>
            <Tag className="h-3 w-3" /> {CATEGORY_LABELS[doc.category] ?? doc.category}
          </span>
          <span className={`${badge} bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 ring-emerald-200 dark:ring-emerald-500/30`}>
            <LockKeyhole className="h-3 w-3" /> {VISIBILITY_LABELS[doc.visibility] ?? doc.visibility}
          </span>
          {doc.courseId && (
            <Link to={`/courses/${doc.courseId}`} className={`${badge} bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 ring-indigo-200 dark:ring-indigo-500/30 hover:bg-indigo-100`}>
              <GraduationCap className="h-3 w-3" /> {doc.courseCode ? `${doc.courseCode} · ` : ''}{doc.courseTitle}
            </Link>
          )}
          {!doc.hasExtractedText && (
            <span className={`${badge} bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-300 ring-amber-200 dark:ring-amber-500/30`}>
              Текст не распознан
            </span>
          )}
        </div>
      </div>
      <div className="flex shrink-0 gap-2">
        {canDeleteDocument(doc) && (
          <button
            type="button"
            onClick={onDelete}
            disabled={busy}
            className="flex items-center gap-2 rounded-lg border border-red-200 dark:border-red-500/30 px-3.5 py-2 text-sm font-medium text-red-600 dark:text-red-400 transition hover:bg-red-50 dark:hover:bg-red-500/10 disabled:opacity-60"
          >
            <Trash2 className="h-4 w-4" /> Удалить
          </button>
        )}
        <button
          type="button"
          onClick={onDownload}
          disabled={busy}
          className="flex items-center gap-2 rounded-lg bg-slate-900 dark:bg-slate-700 px-3.5 py-2 text-sm font-semibold text-white transition hover:bg-slate-800 dark:hover:bg-slate-600 disabled:opacity-60"
        >
          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />} Скачать
        </button>
      </div>
    </section>
  )
}

function ErrorState({ error }) {
  const forbidden = error.status === 403
  return (
    <div className="mx-auto mt-16 flex max-w-md flex-col items-center text-center">
      <span className={`flex h-14 w-14 items-center justify-center rounded-2xl ${forbidden ? 'bg-amber-50 dark:bg-amber-500/10 text-amber-600' : 'bg-slate-100 dark:bg-slate-800 text-slate-500'}`}>
        <ShieldAlert className="h-7 w-7" />
      </span>
      <h1 className="mt-4 text-lg font-semibold text-slate-900 dark:text-white">
        {forbidden ? 'Нет доступа к документу' : error.status === 404 ? 'Документ не найден' : 'Не удалось открыть документ'}
      </h1>
      <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
        {forbidden ? 'Документ виден только участникам курса или преподавателям. Обратитесь к владельцу документа.' : error.message}
      </p>
    </div>
  )
}
