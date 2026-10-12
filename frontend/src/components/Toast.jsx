import { useEffect } from 'react'
import { AlertCircle, CheckCircle2, X } from 'lucide-react'

// Errors stay longer: they often explain what to do next. Both can be closed by hand at any time.
const AUTO_CLOSE_MS = { success: 4000, error: 8000 }

// Pop-up notification in the bottom-right corner; closes itself after a few seconds.
// Pass a new `toast` object ({ id, type, message }) to show the next one.
export default function Toast({ toast, onClose }) {
  useEffect(() => {
    if (!toast) return undefined
    const timer = setTimeout(onClose, AUTO_CLOSE_MS[toast.type] ?? AUTO_CLOSE_MS.success)
    return () => clearTimeout(timer)
  }, [toast, onClose])

  if (!toast) return null
  const isError = toast.type === 'error'
  const Icon = isError ? AlertCircle : CheckCircle2

  return (
    <div className="pointer-events-none fixed inset-x-4 bottom-4 z-[60] flex justify-end sm:inset-x-auto sm:right-6 sm:bottom-6">
      <div
        key={toast.id}
        role={isError ? 'alert' : 'status'}
        className={`animate-step-in pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-xl border bg-white dark:bg-slate-900 p-4 shadow-lg ${
          isError ? 'border-red-200 dark:border-red-500/30' : 'border-emerald-200 dark:border-emerald-500/30'
        }`}
      >
        <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${isError ? 'text-red-600 dark:text-red-400' : 'text-emerald-600 dark:text-emerald-400'}`} />
        <p className="min-w-0 flex-1 break-words text-sm text-slate-700 dark:text-slate-200">{toast.message}</p>
        <button
          type="button"
          onClick={onClose}
          className="rounded p-0.5 text-slate-500 dark:text-slate-400 transition hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-600 dark:hover:text-slate-300"
          aria-label="Закрыть уведомление"
        >
          <X className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}
