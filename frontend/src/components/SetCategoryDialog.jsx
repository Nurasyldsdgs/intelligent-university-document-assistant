import { useId, useRef, useState } from 'react'
import { Loader2, Tags, X } from 'lucide-react'
import api from '../api/axios'
import { useToast } from '../context/ToastContext'
import useDialogFocus from '../hooks/useDialogFocus'
import { CATEGORY_LABELS, CATEGORY_OPTIONS, TEACHER_ONLY_CATEGORY } from '../constants/documents'

/**
 * "Указать тип вручную" (POST /documents/{id}/category): the course teacher sets the type the AI could not find,
 * e.g. for a scan whose text was not recognized. Exam answers become visible to the teacher only.
 */
export default function SetCategoryDialog({ doc, onClose, onSaved }) {
  const titleId = useId()
  const dialogRef = useRef(null)
  const { notifyError, showSuccess } = useToast()
  const [category, setCategory] = useState(CATEGORY_OPTIONS.some((option) => option.value === doc.category) ? doc.category : CATEGORY_OPTIONS[0].value)
  const [saving, setSaving] = useState(false)
  useDialogFocus(dialogRef, { onClose, busy: saving })

  const save = async (event) => {
    event.preventDefault()
    setSaving(true)
    try {
      const { data } = await api.post(`/documents/${doc.id}/category`, { category })
      showSuccess(`Тип документа: ${CATEGORY_LABELS[category] ?? category}`)
      onSaved(data)
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
        className="animate-step-in w-full max-w-md rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-2xl"
      >
        <div className="flex items-start justify-between gap-3">
          <h2 id={titleId} className="flex items-center gap-2 text-lg font-semibold text-slate-900 dark:text-white">
            <Tags className="h-4 w-4 text-indigo-600 dark:text-indigo-400" aria-hidden="true" /> Указать тип вручную
          </h2>
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            aria-label="Закрыть"
            className="rounded-lg p-1.5 text-slate-500 dark:text-slate-400 transition hover:bg-slate-100 dark:hover:bg-slate-800 disabled:opacity-60"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <form onSubmit={save} className="mt-4 space-y-3">
          <label htmlFor={`${titleId}-category`} className="block text-xs font-medium text-slate-600 dark:text-slate-300">
            Тип документа
          </label>
          <select
            id={`${titleId}-category`}
            value={category}
            onChange={(event) => setCategory(event.target.value)}
            className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-sm text-slate-900 dark:text-white outline-none focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100 dark:focus:ring-indigo-500/30"
          >
            {CATEGORY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
          {category === TEACHER_ONLY_CATEGORY && (
            <p className="rounded-lg bg-amber-50 dark:bg-amber-500/10 px-3 py-2 text-xs text-amber-900 dark:text-amber-200">
              Ответы к экзамену будут видны только преподавателю курса.
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
              disabled={saving}
              className="flex items-center gap-2 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 disabled:opacity-60"
            >
              {saving && <Loader2 className="h-4 w-4 animate-spin" />} Сохранить
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
