import { useCallback, useEffect, useState } from 'react'
import { Loader2, UserMinus, Users } from 'lucide-react'
import api from '../api/axios'
import { useToast } from '../context/ToastContext'

/** The course's students, for its teacher and ADMIN; removing a student also removes their tasks of the course. */
export default function CourseStudents({ courseId, onChange }) {
  const { notifyError, showSuccess } = useToast()
  const [page, setPage] = useState(null)
  const [busyId, setBusyId] = useState(null)

  const load = useCallback(() => api.get(`/courses/${courseId}/students`, { params: { size: 100 } })
    .then(({ data }) => setPage(data)), [courseId])

  useEffect(() => {
    load().catch(notifyError)
  }, [load, notifyError])

  const remove = async (student) => {
    if (!window.confirm(`Отчислить ${student.fullName || student.email} с курса? Его задачи по курсу будут удалены.`)) return
    setBusyId(student.id)
    try {
      await api.delete(`/courses/${courseId}/students/${student.id}`)
      await load()
      onChange?.()
      showSuccess('Студент отчислен с курса')
    } catch (err) {
      notifyError(err)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
      <h2 className="flex items-center gap-2 border-b border-slate-200 dark:border-slate-800 px-5 py-3.5 text-sm font-semibold text-slate-900 dark:text-white">
        <Users className="h-4 w-4 text-indigo-600" /> Студенты курса
        {page && <span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:text-slate-300">{page.totalElements}</span>}
      </h2>
      {!page ? (
        <div className="flex items-center justify-center gap-2 py-6 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Загрузка…</div>
      ) : page.content.length === 0 ? (
        <p className="px-5 py-6 text-center text-sm text-slate-500 dark:text-slate-400">Пока никто не записан. Отправьте студентам ссылку на курс.</p>
      ) : (
        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {page.content.map((student) => (
            <li key={student.id} className="flex items-center gap-3 px-5 py-2.5">
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-slate-900 dark:text-white">{student.fullName || '—'}</span>
                <span className="block truncate text-xs text-slate-500 dark:text-slate-400">{student.email}</span>
              </span>
              <button
                type="button"
                onClick={() => remove(student)}
                disabled={busyId === student.id}
                className="inline-flex items-center gap-1 rounded-lg border border-slate-200 dark:border-slate-700 px-2 py-1 text-xs font-medium text-slate-600 dark:text-slate-300 transition hover:border-red-200 hover:text-red-600 disabled:opacity-60"
                title="Отчислить с курса"
              >
                {busyId === student.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <UserMinus className="h-3.5 w-3.5" />}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
