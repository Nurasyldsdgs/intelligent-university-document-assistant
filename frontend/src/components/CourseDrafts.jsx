import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, CalendarClock, Copy, FileText, Loader2, Pencil, Send, Sparkles, Trash2 } from 'lucide-react'
import api, { describeError } from '../api/axios'
import { useToast } from '../context/ToastContext'
import useConfirm from '../hooks/useConfirm'
import ErrorState from './ErrorState'
import AssignmentEditDialog from './AssignmentEditDialog'
import { formatDate, formatDateTime } from '../utils/dates'
import { countAssignments } from '../utils/plural'

// Enough for every draft one analysis can produce (the AI returns at most 50 per document).
const PAGE_SIZE = 100

/**
 * "Черновики от ИИ": assignments the AI found in the course's documents, waiting for the teacher (or ADMIN) to review
 * them. Students see nothing of them until they are published; publishing hands them out to every enrolled student.
 * Render it only for those who manage the course.
 */
export default function CourseDrafts({ courseId, version = 0, onPublished }) {
  const { notifyError, showSuccess } = useToast()
  const [confirm, confirmDialog] = useConfirm()
  const [page, setPage] = useState(null)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [busy, setBusy] = useState(null) // draft id, or 'all'
  const [editing, setEditing] = useState(null) // the draft in the edit dialog

  const load = useCallback(() => api.get(`/courses/${courseId}/assignments/drafts`, { params: { size: PAGE_SIZE } })
    .then(({ data }) => {
      setPage(data)
      setError('')
    }), [courseId])

  useEffect(() => {
    load().catch((err) => setError(describeError(err).message))
  }, [load, version, attempt])

  // Coming from an upload's "Проверить черновики" link (/courses/:id#drafts): scroll here once the list is in.
  const loaded = page !== null
  useEffect(() => {
    if (loaded && window.location.hash === '#drafts') {
      document.getElementById('drafts')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }, [loaded])

  const retry = () => {
    setError('')
    setPage(null)
    setAttempt((value) => value + 1)
  }

  const run = async (key, action) => {
    setBusy(key)
    try {
      await action()
    } catch (err) {
      notifyError(err)
    } finally {
      setBusy(null)
    }
  }

  const publish = (draft) => run(draft.id, async () => {
    // A suspected duplicate or a missing deadline is published only after a confirmation that names the reason.
    const warnings = [
      draft.duplicateOfId && `Похоже на уже существующее задание ${similarTo(draft)}: студенты могут получить его дважды.`,
      !draft.deadline && 'Нет срока сдачи: студенты увидят задание как «Без срока».',
    ].filter(Boolean)
    if (warnings.length > 0) {
      const confirmed = await confirm({
        title: draft.duplicateOfId ? 'Опубликовать возможный дубль?' : 'Опубликовать без дедлайна?',
        message: `«${draft.title}». ${warnings.join(' ')}`,
        confirmLabel: 'Опубликовать',
        tone: 'primary',
      })
      if (!confirmed) return
    }
    await api.post(`/assignments/${draft.id}/publish`)
    await load()
    onPublished?.()
    showSuccess(`Задание «${draft.title}» опубликовано и выдано студентам курса`)
  })

  const remove = (draft) => run(draft.id, async () => {
    const confirmed = await confirm({
      title: 'Удалить черновик?',
      message: `«${draft.title}» будет удалён. Студенты его не видели, их это не затронет.`,
    })
    if (!confirmed) return
    await api.delete(`/assignments/${draft.id}`)
    await load()
    showSuccess('Черновик удалён')
  })

  const notDuplicate = (draft) => run(draft.id, async () => {
    await api.post(`/assignments/${draft.id}/not-duplicate`)
    await load()
    showSuccess('Отметка «похоже на» снята')
  })

  const publishAll = () => run('all', async () => {
    const drafts = page.content
    const withoutDeadline = drafts.filter((draft) => !draft.deadline).length
    const duplicates = drafts.filter((draft) => draft.duplicateOfId).length
    const confirmed = await confirm({
      title: `Опубликовать ${countAssignments(drafts.length)}?`,
      message: `Их получат все студенты курса.${duplicates > 0
        ? ` Похожи на уже существующие задания: ${duplicates} — проверьте, не дубли ли это.` : ''}${withoutDeadline > 0
        ? ` Без дедлайна: ${withoutDeadline} — студенты увидят их как «Без срока».` : ''}`,
      confirmLabel: 'Опубликовать все',
      tone: 'primary',
    })
    if (!confirmed) return
    const { data } = await api.post(`/courses/${courseId}/assignments/publish`, { ids: drafts.map((draft) => draft.id) })
    await load()
    onPublished?.()
    showSuccess(`Опубликовано: ${countAssignments(data.published)}`)
  })

  const count = page?.totalElements ?? 0
  const disabled = busy !== null

  return (
    <section id="drafts" className="scroll-mt-20 rounded-2xl border border-violet-200 dark:border-violet-500/30 bg-white dark:bg-slate-900 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-violet-100 dark:border-violet-500/20 px-5 py-3.5">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-white">
          <Sparkles className="h-4 w-4 text-violet-600 dark:text-violet-400" /> Черновики от ИИ ({count})
        </h2>
        {count > 1 && (
          <button
            type="button"
            onClick={publishAll}
            disabled={disabled}
            className="flex items-center gap-1.5 rounded-lg bg-violet-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-violet-700 disabled:opacity-60"
          >
            {busy === 'all' ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
            Опубликовать все
          </button>
        )}
      </div>
      <div className="p-4">
        {error ? (
          <ErrorState title="Не удалось загрузить черновики" message={error} onRetry={retry} />
        ) : !page ? (
          <div className="flex items-center justify-center gap-2 py-6 text-sm text-slate-500 dark:text-slate-400">
            <Loader2 className="h-4 w-4 animate-spin" /> Загрузка…
          </div>
        ) : page.content.length === 0 ? (
          <p className="py-2 text-center text-sm text-slate-500 dark:text-slate-400">
            Черновиков нет. Задания, которые ИИ найдёт в новых силлабусах и заданиях, появятся здесь — студенты увидят
            их только после публикации.
          </p>
        ) : (
          <>
            <p className="mb-3 text-xs text-slate-600 dark:text-slate-300">
              Студенты пока не видят эти задания. Проверьте название, срок и требования, затем опубликуйте.
            </p>
            <ul className="space-y-2">
              {page.content.map((draft) => (
                <DraftItem
                  key={draft.id}
                  draft={draft}
                  courseId={courseId}
                  busy={busy === draft.id}
                  disabled={disabled}
                  onPublish={publish}
                  onEdit={setEditing}
                  onDelete={remove}
                  onNotDuplicate={notDuplicate}
                />
              ))}
            </ul>
            {!page.last && (
              <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
                Показаны первые {page.content.length} из {page.totalElements}; остальные появятся после публикации этих.
              </p>
            )}
          </>
        )}
      </div>
      {confirmDialog}
      {editing && <AssignmentEditDialog assignment={editing} onClose={() => setEditing(null)} onSaved={() => load().catch(notifyError)} />}
    </section>
  )
}

/** "«Лабораторная работа 1» (до 20 окт. 2026 г.)": the assignment a draft looks like. */
const similarTo = (draft) => `«${draft.duplicateOfTitle}»${draft.duplicateOfDeadline ? ` (до ${formatDate(draft.duplicateOfDeadline)})` : ''}`

function DraftItem({ draft, courseId, busy, disabled, onPublish, onEdit, onDelete, onNotDuplicate }) {
  return (
    <li className="rounded-xl border border-slate-200 dark:border-slate-800 p-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0 flex-1">
          <p className="break-words text-sm font-medium text-slate-900 dark:text-white">{draft.title}</p>
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
            {draft.deadline ? (
              <span className="inline-flex items-center gap-1 text-slate-600 dark:text-slate-300">
                <CalendarClock className="h-3.5 w-3.5" /> {formatDateTime(draft.deadline)}
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 font-medium text-amber-700 dark:text-amber-300">
                <AlertTriangle className="h-3.5 w-3.5" /> Без дедлайна
              </span>
            )}
            {draft.sourceDocumentId && (
              <Link
                to={`/documents/${draft.sourceDocumentId}`}
                state={{ from: `/courses/${courseId}` }}
                className="inline-flex min-w-0 items-center gap-1 text-indigo-600 dark:text-indigo-400 hover:underline"
                title={draft.sourceDocumentName}
              >
                <FileText className="h-3.5 w-3.5 shrink-0" />
                <span className="max-w-56 truncate">{draft.sourceDocumentName ?? 'Документ'}</span>
              </Link>
            )}
          </div>
        </div>
        <div className="flex shrink-0 gap-2">
          <button
            type="button"
            onClick={() => onPublish(draft)}
            disabled={disabled}
            className="flex items-center gap-1.5 rounded-lg border border-violet-200 dark:border-violet-500/30 px-2.5 py-1.5 text-xs font-medium text-violet-700 dark:text-violet-300 transition hover:bg-violet-50 dark:hover:bg-violet-500/10 disabled:opacity-60"
          >
            {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />} Опубликовать
          </button>
          <button
            type="button"
            onClick={() => onEdit(draft)}
            disabled={disabled}
            aria-label={`Изменить черновик «${draft.title}»`}
            title="Изменить название, требования или дедлайн"
            className="flex items-center gap-1.5 rounded-lg border border-slate-300 dark:border-slate-700 px-2.5 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60"
          >
            <Pencil className="h-3.5 w-3.5" /> <span className="hidden sm:inline">Изменить</span>
          </button>
          <button
            type="button"
            onClick={() => onDelete(draft)}
            disabled={disabled}
            aria-label={`Удалить черновик «${draft.title}»`}
            title="Удалить черновик"
            className="flex items-center gap-1.5 rounded-lg border border-red-200 dark:border-red-500/30 px-2.5 py-1.5 text-xs font-medium text-red-600 dark:text-red-400 transition hover:bg-red-50 dark:hover:bg-red-500/10 disabled:opacity-60"
          >
            <Trash2 className="h-3.5 w-3.5" /> <span className="hidden sm:inline">Удалить</span>
          </button>
        </div>
      </div>
      {draft.description && (
        <p className="mt-2 whitespace-pre-line break-words text-xs text-slate-600 dark:text-slate-300">{draft.description}</p>
      )}
      {draft.duplicateOfId && (
        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-lg bg-amber-50 dark:bg-amber-500/10 px-3 py-2 text-xs text-amber-900 dark:text-amber-200">
          <span className="flex min-w-0 items-center gap-1.5">
            <Copy className="h-3.5 w-3.5 shrink-0" />
            <span className="min-w-0 break-words">Похоже на: {similarTo(draft)}</span>
          </span>
          <span className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => onDelete(draft)}
              disabled={disabled}
              className="rounded-md border border-red-200 dark:border-red-500/30 bg-white dark:bg-slate-900 px-2 py-1 font-medium text-red-600 dark:text-red-400 transition hover:bg-red-50 dark:hover:bg-red-500/10 disabled:opacity-60"
            >
              Это дубль — удалить черновик
            </button>
            <button
              type="button"
              onClick={() => onNotDuplicate(draft)}
              disabled={disabled}
              className="rounded-md border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-2 py-1 font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60"
            >
              Это другое задание
            </button>
          </span>
        </div>
      )}
    </li>
  )
}
