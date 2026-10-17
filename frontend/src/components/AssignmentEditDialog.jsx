import { useId, useRef, useState } from 'react'
import { Loader2, Pencil, X } from 'lucide-react'
import api from '../api/axios'
import { useToast } from '../context/ToastContext'
import useConfirm from '../hooks/useConfirm'
import useDialogFocus from '../hooks/useDialogFocus'

const inputClass = 'w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-sm text-slate-900 dark:text-white outline-none transition placeholder:text-slate-500 dark:placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100 dark:focus:ring-indigo-500/30'

// The backend keeps deadlines as wall-clock time of the university (no zone), like the datetime-local input.
const toInputValue = (deadline) => (deadline ? deadline.slice(0, 16) : '')

/**
 * Edits an assignment's title, requirements and deadline (PATCH /assignments/{id}), e.g. to fix what the AI got
 * wrong. A published assignment's changes reach the students' tasks at once; their progress and submissions stay.
 */
export default function AssignmentEditDialog({ assignment, onClose, onSaved }) {
  const titleId = useId()
  const dialogRef = useRef(null)
  const { notifyError, showSuccess } = useToast()
  const [confirm, confirmDialog] = useConfirm()
  const [form, setForm] = useState({
    title: assignment.title ?? '',
    description: assignment.description ?? '',
    deadline: toInputValue(assignment.deadline),
  })
  const [saving, setSaving] = useState(false)
  useDialogFocus(dialogRef, { onClose, busy: saving })

  const published = assignment.status !== 'DRAFT'
  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }))

  const save = async (event) => {
    event.preventDefault()
    const deadlineChanged = form.deadline !== toInputValue(assignment.deadline)
    if (deadlineChanged && form.deadline && new Date(form.deadline) < new Date()) {
      const confirmed = await confirm({
        title: 'Дедлайн уже прошёл',
        message: 'Выбранные дата и время уже в прошлом: студенты увидят задание просроченным. Сохранить всё равно?',
        confirmLabel: 'Сохранить',
        tone: 'primary',
      })
      if (!confirmed) return
    }
    setSaving(true)
    try {
      const { data } = await api.patch(`/assignments/${assignment.id}`, {
        title: form.title.trim(),
        description: form.description.trim() || null,
        deadline: form.deadline ? `${form.deadline}:00` : null,
      })
      showSuccess(published ? 'Задание сохранено, студенты видят изменения' : 'Черновик сохранён')
      onSaved?.(data)
      onClose()
    } catch (err) {
      notifyError(err)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !saving) onClose()
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="animate-step-in w-full max-w-lg rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-2xl"
      >
        <div className="flex items-start justify-between gap-3">
          <h2 id={titleId} className="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <Pencil className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
            {published ? 'Изменить задание' : 'Изменить черновик'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            aria-label="Закрыть"
            className="rounded-lg p-1.5 text-slate-500 dark:text-slate-400 transition hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-700 dark:hover:text-slate-200 disabled:opacity-60"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <form onSubmit={save} className="mt-4 space-y-3">
          <div>
            <label htmlFor={`${titleId}-title`} className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">Название</label>
            <input id={`${titleId}-title`} value={form.title} onChange={update('title')} required maxLength={255} className={inputClass} />
          </div>
          <div>
            <label htmlFor={`${titleId}-req`} className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">Требования</label>
            <textarea id={`${titleId}-req`} value={form.description} onChange={update('description')} rows={4} maxLength={2000} className={`${inputClass} resize-y`} />
          </div>
          <div>
            <label htmlFor={`${titleId}-deadline`} className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">Дедлайн</label>
            <div className="flex gap-2">
              <input id={`${titleId}-deadline`} type="datetime-local" value={form.deadline} onChange={update('deadline')} className={inputClass} />
              {form.deadline && (
                <button
                  type="button"
                  onClick={() => setForm((current) => ({ ...current, deadline: '' }))}
                  className="shrink-0 rounded-lg border border-slate-300 dark:border-slate-700 px-3 text-xs font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800"
                >
                  Без срока
                </button>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">Время указывается по часовому поясу вуза.</p>
          </div>
          {published && (
            <p className="rounded-lg bg-indigo-50 dark:bg-indigo-500/10 px-3 py-2 text-xs text-indigo-800 dark:text-indigo-200">
              Студенты курса сразу увидят изменения; их статусы и сданные решения сохранятся.
            </p>
          )}
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              className="rounded-lg border border-slate-300 dark:border-slate-700 px-4 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800/50 disabled:opacity-60"
            >
              Отмена
            </button>
            <button
              type="submit"
              disabled={saving || !form.title.trim()}
              className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 disabled:opacity-60"
            >
              {saving && <Loader2 className="h-4 w-4 animate-spin" />} Сохранить
            </button>
          </div>
        </form>
      </div>
      {confirmDialog}
    </div>
  )
}
