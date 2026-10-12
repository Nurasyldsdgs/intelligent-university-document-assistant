import { useId, useRef } from 'react'
import { AlertTriangle, Loader2 } from 'lucide-react'
import useDialogFocus from '../hooks/useDialogFocus'

const TONES = {
  danger: {
    icon: 'bg-red-100 dark:bg-red-500/20 text-red-600 dark:text-red-400',
    button: 'bg-red-600 hover:bg-red-700 focus:ring-red-200 dark:focus:ring-red-500/30',
  },
  primary: {
    icon: 'bg-indigo-100 dark:bg-indigo-500/20 text-indigo-600 dark:text-indigo-400',
    button: 'bg-indigo-600 hover:bg-indigo-700 focus:ring-indigo-200 dark:focus:ring-indigo-500/30',
  },
}

// Modal confirmation, focus kept inside (red "danger" tone by default, for deletes). Escape or a click on the backdrop cancels (unless busy).
export default function ConfirmDialog({ title, children, confirmLabel = 'Удалить', tone = 'danger', loading, onConfirm, onCancel }) {
  const colors = TONES[tone] ?? TONES.danger
  const titleId = useId()
  const dialogRef = useRef(null)
  useDialogFocus(dialogRef, { onClose: onCancel, busy: loading })

  return (
    <div
      className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !loading) onCancel()
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
        <div className="flex items-start gap-4">
          <span className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full ${colors.icon}`}>
            <AlertTriangle className="h-5 w-5" />
          </span>
          <div className="min-w-0">
            <h2 id={titleId} className="text-lg font-semibold text-slate-900 dark:text-white">{title}</h2>
            <div className="mt-2 break-words text-sm text-slate-600 dark:text-slate-300">{children}</div>
          </div>
        </div>
        <div className="mt-6 flex justify-end gap-3">
          <button
            type="button"
            onClick={onCancel}
            disabled={loading}
            autoFocus
            className="rounded-lg border border-slate-300 dark:border-slate-700 px-4 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800/50 disabled:opacity-60"
          >
            Отмена
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={loading}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold text-white shadow-sm transition focus:outline-none focus:ring-4 disabled:opacity-70 ${colors.button}`}
          >
            {loading && <Loader2 className="h-4 w-4 animate-spin" />}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
