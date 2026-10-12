import { useEffect, useState } from 'react'
import { CalendarClock, Loader2 } from 'lucide-react'
import api, { describeError } from '../api/axios'
import ErrorState from './ErrorState'
import TaskItem from './TaskItem'

/**
 * "Upcoming Deadlines": the student's unfinished tasks due from now on, nearest first (GET /tasks/upcoming).
 * {@code statusOverrides} ({ taskId: status }) are changes just made in "Мои задачи": a task marked COMPLETED leaves
 * this list at once, and {@code refreshKey} then reloads it so the next deadline moves up.
 */
export default function UpcomingDeadlines({ limit = 5, refreshKey = 0, statusOverrides = {} }) {
  const [tasks, setTasks] = useState(null)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    api.get('/tasks/upcoming', { params: { limit } })
      .then(({ data }) => {
        if (cancelled) return
        setTasks(data)
        setError('')
      })
      .catch((err) => !cancelled && setError(describeError(err).message))
    return () => {
      cancelled = true
    }
  }, [limit, refreshKey, attempt])

  const visible = (tasks ?? [])
    .map((task) => (statusOverrides[task.id] ? { ...task, status: statusOverrides[task.id] } : task))
    .filter((task) => task.status !== 'COMPLETED')

  return (
    <section className="rounded-2xl border border-amber-200/70 dark:border-amber-500/30 bg-white dark:bg-slate-900 p-5 shadow-sm">
      <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900 dark:text-white">
        <CalendarClock className="h-5 w-5 text-amber-600 dark:text-amber-400" />
        Ближайшие дедлайны
      </h2>
      {error ? (
        <ErrorState
          title="Не удалось загрузить дедлайны"
          message={error}
          onRetry={() => {
            setError('')
            setTasks(null)
            setAttempt((value) => value + 1)
          }}
        />
      ) : tasks === null ? (
        <div className="flex items-center justify-center gap-2 py-8 text-sm text-slate-500 dark:text-slate-400">
          <Loader2 className="h-4 w-4 animate-spin" /> Загрузка…
        </div>
      ) : visible.length === 0 ? (
        <p className="mt-4 rounded-xl border border-dashed border-slate-300 dark:border-slate-700 px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">
          Предстоящих дедлайнов нет.
        </p>
      ) : (
        <ul className="mt-4 space-y-2">
          {visible.map((task) => <TaskItem key={task.id} task={task} showDocument />)}
        </ul>
      )}
    </section>
  )
}
