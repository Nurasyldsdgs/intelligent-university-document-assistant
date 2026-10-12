import { useCallback, useEffect, useState } from 'react'
import { ListTodo, Loader2 } from 'lucide-react'
import api from '../api/axios'
import { useToast } from '../context/ToastContext'
import TaskItem from './TaskItem'
import TaskSubmissions from './TaskSubmissions'

const PAGE_SIZE = 10
const FILTERS = [
  { value: '', label: 'Все' },
  { value: 'PENDING', label: 'К выполнению' },
  { value: 'IN_PROGRESS', label: 'В работе' },
  { value: 'COMPLETED', label: 'Выполнено' },
]

/**
 * "Мои задачи": GET /tasks/my, nearest deadline first, with a status filter, paging and status changes.
 * {@code onStatusChanged(task)} tells the page, so "Ближайшие дедлайны" can follow at once.
 */
export default function MyTasks({ onStatusChanged }) {
  const { notifyError } = useToast()
  const [filter, setFilter] = useState('')
  const [page, setPage] = useState(null) // Last loaded page; its content holds all tasks loaded so far.
  const [loading, setLoading] = useState(true)
  const [updatingId, setUpdatingId] = useState(null)

  const fetchPage = useCallback((number) => api.get('/tasks/my', {
    params: { page: number, size: PAGE_SIZE, ...(filter ? { status: filter } : {}) },
  }).then(({ data }) => data), [filter])

  // Loading is switched on by selectFilter (and initially), not here: no synchronous setState in the effect.
  useEffect(() => {
    let cancelled = false
    fetchPage(0)
      .then((data) => !cancelled && setPage(data))
      .catch((err) => !cancelled && notifyError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [fetchPage, notifyError])

  const selectFilter = (value) => {
    if (value === filter) return
    setLoading(true)
    setFilter(value)
  }

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

  const changeStatus = async (task, status) => {
    setUpdatingId(task.id)
    try {
      const { data } = await api.put(`/tasks/${task.id}/status`, { status })
      onStatusChanged?.(data)
      setPage((current) => ({
        ...current,
        // Under a status filter, a task moved to another status leaves the list.
        content: filter && data.status !== filter
          ? current.content.filter((item) => item.id !== task.id)
          : current.content.map((item) => (item.id === task.id ? data : item)),
        totalElements: filter && data.status !== filter ? current.totalElements - 1 : current.totalElements,
      }))
    } catch (err) {
      notifyError(err)
    } finally {
      setUpdatingId(null)
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900 dark:text-white">
          <ListTodo className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
          Мои задачи
          {page && <span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:text-slate-300">{page.totalElements}</span>}
        </h2>
        <div className="flex flex-wrap gap-1 rounded-xl bg-slate-100 dark:bg-slate-800 p-1" role="group" aria-label="Фильтр по статусу">
          {FILTERS.map((item) => (
            <button
              key={item.value}
              type="button"
              aria-pressed={filter === item.value}
              onClick={() => selectFilter(item.value)}
              className={`rounded-lg px-2.5 py-1 text-xs font-medium transition focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
                filter === item.value
                  ? 'bg-white dark:bg-slate-900 text-indigo-700 dark:text-indigo-300 shadow-sm'
                  : 'text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white'
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {!page && loading ? (
        <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500 dark:text-slate-400">
          <Loader2 className="h-4 w-4 animate-spin" /> Загрузка задач…
        </div>
      ) : page?.content.length === 0 ? (
        <p className="mt-4 rounded-xl border border-dashed border-slate-300 dark:border-slate-700 px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400">
          {filter ? 'Нет задач с таким статусом.' : 'Задач пока нет. Они появятся, когда преподаватель выдаст задания или ИИ найдёт их в силлабусах ваших курсов.'}
        </p>
      ) : page && (
        <>
          <ul className={`mt-4 space-y-2 transition-opacity ${loading ? 'opacity-60' : ''}`}>
            {page.content.map((task) => (
              <TaskItem
                key={task.id}
                task={task}
                showDocument
                onStatusChange={changeStatus}
                updating={updatingId === task.id}
                extra={task.assignmentId && <TaskSubmissions taskId={task.id} count={task.submissionCount ?? 0} />}
              />
            ))}
          </ul>
          {!page.last && (
            <button
              type="button"
              onClick={loadMore}
              disabled={loading}
              className="mt-3 flex w-full items-center justify-center gap-2 rounded-lg border border-slate-300 dark:border-slate-700 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60"
            >
              {loading && <Loader2 className="h-4 w-4 animate-spin" />}
              Показать ещё
            </button>
          )}
        </>
      )}
    </section>
  )
}
