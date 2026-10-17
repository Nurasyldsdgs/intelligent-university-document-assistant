import { FileText, LockKeyhole } from 'lucide-react'

/**
 * In place of the preview when the original file holds personal data the course teacher has not opened to students:
 * the masked text is in the "Текст" tab ({@code onShowText} switches to it).
 */
export default function RestrictedOriginalNotice({ onShowText }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 px-4 py-14 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300">
        <LockKeyhole className="h-6 w-6" aria-hidden="true" />
      </span>
      <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Оригинал доступен только преподавателю</p>
      <p className="max-w-md text-xs text-slate-600 dark:text-slate-300">
        В файле есть личные данные. Текст документа доступен — в нём эти данные скрыты.
      </p>
      {onShowText && (
        <button
          type="button"
          onClick={onShowText}
          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-1.5 text-sm font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800"
        >
          <FileText className="h-4 w-4" aria-hidden="true" /> Открыть текст
        </button>
      )}
    </div>
  )
}
