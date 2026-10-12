import { useCallback, useEffect, useState } from 'react'
import { ClipboardList, Loader2, Plus } from 'lucide-react'
import api from '../api/axios'
import { useToast } from '../context/ToastContext'
import TaskItem from './TaskItem'
import TaskSubmissions from './TaskSubmissions'

const inputClass = 'w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-sm text-slate-900 dark:text-white outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100 dark:focus:ring-indigo-500/30'

/**
 * The course's assignments (found by the AI in its documents or added by hand). Every enrolled student gets each one
 * as a task. With {@code canManage} (course teacher, ADMIN) assignments can be added and deleted.
 */
export default function CourseAssignments({ courseId, canManage, version = 0 }) {
  const { notifyError, showSuccess } = useToast()
  const [page, setPage] = useState(null)
  const [busyId, setBusyId] = useState(null)
  const [form, setForm] = useState({ title: '', description: '', deadline: '' })
  const [saving, setSaving] = useState(false)

  const load = useCallback(() => api.get(`/courses/${courseId}/assignments`, { params: { size: 100 } })
    .then(({ data }) => setPage(data)), [courseId])

  useEffect(() => {
    load().catch(notifyError)
  }, [load, notifyError, version])

  const create = async (event) => {
    event.preventDefault()
    setSaving(true)
    try {
      await api.post(`/courses/${courseId}/assignments`, {
        title: form.title.trim(),
        description: form.description.trim() || null,
        // datetime-local gives "2026-10-15T23:59"; the backend expects seconds as well.
        deadline: form.deadline ? `${form.deadline}:00` : null,
      })
      setForm({ title: '', description: '', deadline: '' })
      await load()
      showSuccess('Задание создано и выдано всем студентам курса')
    } catch (err) {
      notifyError(err)
    } finally {
      setSaving(false)
    }
  }

  const remove = async (assignment) => {
    if (!window.confirm(`Удалить задание «${assignment.title}»? Оно исчезнет из задач всех студентов курса.`)) return
    setBusyId(assignment.id)
    try {
      await api.delete(`/assignments/${assignment.id}`)
      await load()
      showSuccess('Задание удалено')
    } catch (err) {
      notifyError(err)
    } finally {
      setBusyId(null)
    }
  }

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }))

  return (
    <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
      <h2 className="flex items-center gap-2 border-b border-slate-200 dark:border-slate-800 px-5 py-3.5 text-sm font-semibold text-slate-900 dark:text-white">
        <ClipboardList className="h-4 w-4 text-indigo-600" /> Задания курса
        {page && <span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:text-slate-300">{page.totalElements}</span>}
      </h2>
      <div className="p-4">
        {!page ? (
          <div className="flex items-center justify-center gap-2 py-6 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Загрузка…</div>
        ) : page.content.length === 0 ? (
          <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
            Заданий пока нет. ИИ находит их в загруженных силлабусах и заданиях{canManage ? ', или добавьте задание вручную' : ''}.
          </p>
        ) : (
          <ul className="space-y-2">
            {page.content.map((assignment) => (
              <TaskItem
                key={assignment.id}
                task={assignment}
                showDocument
                onDelete={canManage ? remove : undefined}
                updating={busyId === assignment.id}
                extra={canManage && <TaskSubmissions key={assignment.id} assignmentId={assignment.id} />}
              />
            ))}
          </ul>
        )}

        {canManage && (
          <form onSubmit={create} className="mt-4 space-y-2 rounded-xl border border-dashed border-slate-300 dark:border-slate-700 p-3">
            <p className="flex items-center gap-1.5 text-xs font-semibold text-slate-600 dark:text-slate-300"><Plus className="h-3.5 w-3.5" /> Новое задание</p>
            <input value={form.title} onChange={update('title')} required maxLength={255} placeholder="Название, например «Practice 04»" className={inputClass} aria-label="Название задания" />
            <textarea value={form.description} onChange={update('description')} rows={2} maxLength={2000} placeholder="Требования (необязательно)" className={`${inputClass} resize-none`} aria-label="Требования" />
            <div className="flex gap-2">
              <input type="datetime-local" value={form.deadline} onChange={update('deadline')} className={inputClass} aria-label="Дедлайн" />
              <button type="submit" disabled={saving || !form.title.trim()} className="flex shrink-0 items-center gap-2 rounded-lg bg-indigo-600 px-3 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:opacity-60">
                {saving && <Loader2 className="h-4 w-4 animate-spin" />} Выдать
              </button>
            </div>
          </form>
        )}
      </div>
    </section>
  )
}
