import { AlertTriangle, RotateCw } from 'lucide-react'

/**
 * A failed load, always with a way to try again. Data blocks keep three distinct states: loading, this error, and
 * empty - an error never shows as an endless "Загрузка…" or as an empty list.
 *
 * @param message what went wrong, in Russian (describeError / getErrorMessage)
 * @param onRetry re-runs the load; without it no button is shown (e.g. 403/404, where retrying cannot help)
 * @param compact a single line, for small spots inside a header or a toolbar
 */
export default function ErrorState({ message, onRetry, title = 'Не удалось загрузить данные', compact = false, className = '' }) {
  const retry = onRetry && (
    <button
      type="button"
      onClick={onRetry}
      className="inline-flex shrink-0 items-center gap-1 rounded-md font-semibold text-indigo-600 dark:text-indigo-400 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
    >
      <RotateCw className="h-3.5 w-3.5" /> Повторить
    </button>
  )

  if (compact) {
    return (
      <p role="alert" className={`flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-amber-800 dark:text-amber-200 ${className}`}>
        <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
        <span>{message}</span>
        {retry && <span aria-hidden="true">·</span>}
        {retry}
      </p>
    )
  }

  return (
    <div role="alert" className={`flex flex-col items-center px-4 py-8 text-center ${className}`}>
      <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-50 dark:bg-amber-500/10 text-amber-600 dark:text-amber-400">
        <AlertTriangle className="h-5 w-5" />
      </span>
      <p className="mt-3 text-sm font-medium text-slate-800 dark:text-slate-100">{title}</p>
      {message && <p className="mt-1 max-w-md text-xs text-slate-600 dark:text-slate-300">{message}</p>}
      {retry && <div className="mt-3 text-sm">{retry}</div>}
    </div>
  )
}
