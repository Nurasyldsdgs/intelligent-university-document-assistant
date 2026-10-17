import { Link, useLocation } from 'react-router-dom'
import { CalendarClock, FileText, Loader2, Lock, Trash2 } from 'lucide-react'
import { TASK_STATUS_LABELS } from '../constants/documents'
import { getDeadlineInfo, getTaskType } from '../utils/tasks'

const STATUS_STYLES = {
  PENDING: 'bg-slate-100 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
  IN_PROGRESS: 'bg-indigo-50 text-indigo-700 ring-indigo-200 dark:bg-indigo-500/10 dark:text-indigo-300 dark:ring-indigo-500/30',
  COMPLETED: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-300 dark:ring-emerald-500/30',
}

const tagClass = 'inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-semibold ring-1 ring-inset'

/**
 * One task - or course assignment - with type and deadline tags. With {@code onStatusChange} the status is a select
 * (own tasks), otherwise a read-only badge when the status is a task status; {@code showDocument} links the source
 * document; {@code onDelete} adds a delete button (assignments, for the course teacher); {@code actions} adds more
 * buttons next to it; {@code extra} is rendered under the tags (submissions). A closed assignment - or a student's
 * task of one - is marked "Закрыто".
 */
export default function TaskItem({ task, onStatusChange, onDelete, actions = null, updating = false, showDocument = false, extra = null }) {
  const type = getTaskType(task)
  const deadline = getDeadlineInfo(task.deadline, task.status)
  const completed = task.status === 'COMPLETED'
  const closed = task.status === 'CLOSED' || task.assignmentStatus === 'CLOSED'
  const documentId = task.documentId ?? task.sourceDocumentId
  const documentName = task.documentName ?? task.sourceDocumentName
  const location = useLocation()

  return (
    <li className="flex flex-col gap-2 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-3 sm:flex-row sm:items-start">
      <div className="min-w-0 flex-1">
        <p className={`text-sm font-medium ${completed ? 'text-slate-500 line-through dark:text-slate-400' : 'text-slate-900 dark:text-white'}`}>
          {task.title}
        </p>
        {task.description && (
          <p className="mt-0.5 line-clamp-2 text-xs text-slate-500 dark:text-slate-400">{task.description}</p>
        )}
        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          <span className={`${tagClass} ${type.badge}`}>{type.label}</span>
          {closed && (
            <span className={`${tagClass} bg-slate-100 text-slate-700 ring-slate-300 dark:bg-slate-800 dark:text-slate-200 dark:ring-slate-600`}
              title="Задание закрыто: новые решения не принимаются">
              <Lock className="h-3 w-3" /> Закрыто
            </span>
          )}
          <span className={`${tagClass} ${deadline.tone}`}>
            <CalendarClock className="h-3 w-3" />
            {deadline.label}
          </span>
          {task.courseCode && (
            <span className={`${tagClass} bg-white text-slate-600 ring-slate-200 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700`}>
              {task.courseCode}
            </span>
          )}
          {showDocument && documentId && (
            <Link
              to={`/documents/${documentId}`}
              state={{ from: location.pathname }}
              className="inline-flex min-w-0 items-center gap-1 text-[11px] font-medium text-indigo-600 dark:text-indigo-400 hover:underline"
              title={documentName}
            >
              <FileText className="h-3 w-3 shrink-0" />
              <span className="max-w-48 truncate">{documentName}</span>
            </Link>
          )}
        </div>
        {extra}
      </div>
      {onStatusChange ? (
        <label className="flex shrink-0 items-center gap-2">
          <span className="sr-only">Статус задачи</span>
          {updating && <Loader2 className="h-3.5 w-3.5 animate-spin text-slate-400" />}
          <select
            value={task.status}
            disabled={updating}
            onChange={(event) => onStatusChange(task, event.target.value)}
            className={`rounded-lg border-0 py-1 pl-2 pr-7 text-xs font-semibold ring-1 ring-inset focus:ring-2 focus:ring-indigo-500 disabled:opacity-60 ${STATUS_STYLES[task.status]}`}
          >
            {Object.entries(TASK_STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </select>
        </label>
      ) : TASK_STATUS_LABELS[task.status] && (
        <span className={`${tagClass} shrink-0 self-start ${STATUS_STYLES[task.status]}`}>{TASK_STATUS_LABELS[task.status]}</span>
      )}
      {actions}
      {onDelete && (
        <button
          type="button"
          onClick={() => onDelete(task)}
          disabled={updating}
          className="inline-flex shrink-0 items-center gap-1 self-start rounded-lg border border-red-200 dark:border-red-500/30 px-2 py-1 text-xs font-medium text-red-600 dark:text-red-400 transition hover:bg-red-50 dark:hover:bg-red-500/10 disabled:opacity-60"
          title="Удалить задание у всех студентов курса"
        >
          {updating ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />} Удалить
        </button>
      )}
    </li>
  )
}
