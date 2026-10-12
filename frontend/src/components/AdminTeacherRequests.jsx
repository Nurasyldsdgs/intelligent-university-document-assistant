import { useCallback, useEffect, useState } from 'react'
import { Check, Loader2, Presentation, X } from 'lucide-react'
import api, { getErrorMessage } from '../api/axios'
import { useToast } from '../context/ToastContext'
import { formatDateTime } from '../utils/dates'
import ErrorState from './ErrorState'

const PAGE_SIZE = 20

/**
 * "Заявки преподавателей": students who asked for the teacher role at registration. ADMIN approves (the account
 * becomes ROLE_TEACHER and has to log in again) or rejects (it stays a student). {@code onDecided(user)} receives
 * the updated user so the users table can refresh its row.
 */
export default function AdminTeacherRequests({ onDecided, refreshKey = 0 }) {
  const { notifyError, showSuccess } = useToast()
  const [page, setPage] = useState(null)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)

  const load = useCallback(() => api.get('/admin/teacher-requests', { params: { page: 0, size: PAGE_SIZE } })
    .then(({ data }) => {
      setPage(data)
      setError('')
    })
    .catch((err) => setError(getErrorMessage(err))), [])

  // refreshKey: the page changed a user's role elsewhere, which may have settled a request.
  useEffect(() => {
    load()
  }, [load, refreshKey])

  const decide = async (request, action) => {
    setBusyId(request.id)
    try {
      const { data } = await api.post(`/admin/teacher-requests/${request.id}/${action}`)
      showSuccess(action === 'approve'
        ? `${request.email} теперь преподаватель. Ему нужно войти заново.`
        : `Заявка ${request.email} отклонена, пользователь остаётся студентом.`)
      onDecided?.(data)
      await load()
    } catch (err) {
      notifyError(err)
    } finally {
      setBusyId(null)
    }
  }

  const total = page?.totalElements ?? 0

  return (
    <section className="mt-8 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
      <div className="border-b border-slate-200 dark:border-slate-800 px-5 py-4">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900 dark:text-white">
          <Presentation className="h-4 w-4 text-amber-600 dark:text-amber-400" />
          Заявки преподавателей
          {total > 0 && (
            <span className="rounded-full bg-amber-100 dark:bg-amber-500/20 px-2 py-0.5 text-xs font-semibold text-amber-800 dark:text-amber-300" aria-label={`Заявок: ${total}`}>
              {total}
            </span>
          )}
        </h2>
        <p className="text-xs text-slate-500 dark:text-slate-400">
          При регистрации пользователь отметил «Я преподаватель». Пока заявка не подтверждена, он работает как студент.
          После подтверждения он отчисляется со своих курсов как студент и должен войти заново.
        </p>
      </div>

      {error ? (
        <ErrorState
          title="Не удалось загрузить заявки"
          message={error}
          onRetry={() => {
            setError('')
            setPage(null)
            load()
          }}
        />
      ) : !page ? (
        <div className="flex items-center justify-center gap-2 py-8 text-sm text-slate-500 dark:text-slate-400">
          <Loader2 className="h-4 w-4 animate-spin" /> Загрузка заявок…
        </div>
      ) : page.content.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-slate-500 dark:text-slate-400">Новых заявок нет.</p>
      ) : (
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {page.content.map((request) => (
            <li key={request.id} className="flex flex-col gap-3 px-5 py-3 sm:flex-row sm:items-center">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-slate-900 dark:text-white">{request.fullName || '—'}</p>
                <p className="truncate text-xs text-slate-500 dark:text-slate-400">
                  {request.email}
                  {!request.emailVerified && <span className="ml-1.5 text-amber-700 dark:text-amber-300">· email не подтверждён</span>}
                </p>
              </div>
              <span className="shrink-0 text-xs text-slate-500 dark:text-slate-400">
                {formatDateTime(request.requestedAt)}
              </span>
              <div className="flex shrink-0 gap-2">
                <button
                  type="button"
                  onClick={() => decide(request, 'approve')}
                  disabled={busyId !== null}
                  className="flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-emerald-700 disabled:opacity-60"
                >
                  {busyId === request.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Check className="h-3.5 w-3.5" />} Подтвердить
                </button>
                <button
                  type="button"
                  onClick={() => decide(request, 'reject')}
                  disabled={busyId !== null}
                  className="flex items-center gap-1.5 rounded-lg border border-red-200 dark:border-red-500/30 px-3 py-1.5 text-xs font-medium text-red-600 dark:text-red-400 transition hover:bg-red-50 dark:hover:bg-red-500/10 disabled:opacity-60"
                >
                  <X className="h-3.5 w-3.5" /> Отклонить
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {page && !page.last && (
        <p className="border-t border-slate-100 dark:border-slate-800 px-5 py-2 text-xs text-slate-500 dark:text-slate-400">
          Показаны первые {page.content.length} из {total}; остальные появятся после обработки этих.
        </p>
      )}
    </section>
  )
}
