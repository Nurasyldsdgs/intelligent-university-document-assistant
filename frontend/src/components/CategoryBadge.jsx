import { Bot, Lock } from 'lucide-react'
import { CATEGORY_LABELS } from '../constants/documents'

const BASE = 'inline-flex shrink-0 items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-semibold ring-1 ring-inset'

// One color per category, in light and dark theme. Legacy TRANSCRIPT/THESIS keep a neutral look.
const STYLES = {
  SYLLABUS: 'bg-indigo-50 text-indigo-700 ring-indigo-200 dark:bg-indigo-500/10 dark:text-indigo-300 dark:ring-indigo-500/30',
  ASSIGNMENT: 'bg-amber-50 text-amber-800 ring-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:ring-amber-500/30',
  LECTURE: 'bg-sky-50 text-sky-700 ring-sky-200 dark:bg-sky-500/10 dark:text-sky-300 dark:ring-sky-500/30',
  EXAM_ANSWERS: 'bg-rose-50 text-rose-700 ring-rose-200 dark:bg-rose-500/10 dark:text-rose-300 dark:ring-rose-500/30',
  REGULATION: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-300 dark:ring-emerald-500/30',
  TRANSCRIPT: 'bg-slate-50 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
  THESIS: 'bg-violet-50 text-violet-700 ring-violet-200 dark:bg-violet-500/10 dark:text-violet-300 dark:ring-violet-500/30',
  OTHER: 'bg-white text-slate-600 ring-slate-200 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700',
}

/**
 * A document category ("Силлабус · 96%"). {@code confidence} (0..1) is shown only when present; {@code ai} marks a
 * category chosen by the AI. EXAM_ANSWERS gets a lock: the backend always keeps such documents from students.
 */
export default function CategoryBadge({ category, confidence = null, ai = false, className = '' }) {
  const label = CATEGORY_LABELS[category] ?? category
  const hidden = category === 'EXAM_ANSWERS'
  const percent = typeof confidence === 'number' ? ` · ${Math.round(confidence * 100)}%` : ''
  return (
    <span
      className={`${BASE} ${STYLES[category] ?? STYLES.OTHER} ${className}`}
      title={hidden ? 'Скрыто от студентов' : ai ? 'Тип определён ИИ' : undefined}
    >
      {hidden && <Lock className="h-3 w-3" aria-label="Скрыто от студентов" />}
      {ai && !hidden && <Bot className="h-3 w-3" aria-hidden="true" />}
      {label}{percent}
    </span>
  )
}
