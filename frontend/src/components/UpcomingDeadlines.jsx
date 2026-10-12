import { useEffect, useState } from 'react'
import { CalendarClock, Loader2 } from 'lucide-react'
import api from '../api/axios'
import { useToast } from '../context/ToastContext'
import TaskItem from './TaskItem'

/** "Upcoming Deadlines": the student's unfinished tasks due from now on, nearest first (GET /tasks/upcoming). */
export default function UpcomingDeadlines({ limit = 5 }) {
  const { notifyError } = useToast()
  const [tasks, setTasks] = useState(null)

  useEffect(() => {
    let cancelled = false
    api.get('/tasks/upcoming', { params: { limit } })
      .then(({ data }) => !cancelled && setTasks(data))
      .catch((err) => {
        if (cancelled) return
        notifyError(err)
        setTasks([])
      })
    return () => {
      cancelled = true
    }
  }, [limit, notifyError])

  return (
    <section className="rounded-2xl border border-amber-200/70 dark:border-amber-500/30 bg-white dark:bg-slate-900 p-5 shadow-sm">
      <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900 dark:text-white">
        <CalendarClock className="h-5 w-5 text-amber-600 dark:text-amber-400" />
        Ближайшие дедлайны
      </h2>
      {tasks === null ? (
        <div className="flex items-center justify-center gap-2 py-8 text-sm text-slate-500 dark:text-slate-400">
          <Loader2 className="h-4 w-4 animate-spin" /> Загрузка…
        </div>
      ) : tasks.length === 0 ? (
        <p className="mt-4 rounded-xl border border-dashed border-slate-300 dark:border-slate-700 px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">
          Предстоящих дедлайнов нет.
        </p>
      ) : (
        <ul className="mt-4 space-y-2">
          {tasks.map((task) => <TaskItem key={task.id} task={task} showDocument />)}
        </ul>
      )}
    </section>
  )
}
