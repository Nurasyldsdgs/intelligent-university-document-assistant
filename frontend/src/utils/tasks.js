import { formatDateTime } from './dates'
// Tasks carry no type of their own; the label is guessed from the words the AI used in the title and description.
const TASK_TYPES = [
  { pattern: /лаборатор|лаб\.|\blab/i, label: 'Лабораторная', badge: 'bg-sky-50 text-sky-700 ring-sky-200 dark:bg-sky-500/10 dark:text-sky-300 dark:ring-sky-500/30' },
  { pattern: /проект|project/i, label: 'Проект', badge: 'bg-violet-50 text-violet-700 ring-violet-200 dark:bg-violet-500/10 dark:text-violet-300 dark:ring-violet-500/30' },
  { pattern: /экзам|зач[её]т|итогов|exam|final/i, label: 'Экзамен', badge: 'bg-rose-50 text-rose-700 ring-rose-200 dark:bg-rose-500/10 dark:text-rose-300 dark:ring-rose-500/30' },
  { pattern: /контрольн|тест|quiz|midterm|рубежн/i, label: 'Контроль', badge: 'bg-orange-50 text-orange-700 ring-orange-200 dark:bg-orange-500/10 dark:text-orange-300 dark:ring-orange-500/30' },
  { pattern: /эссе|реферат|доклад|отч[её]т|essay|report/i, label: 'Письменная работа', badge: 'bg-teal-50 text-teal-700 ring-teal-200 dark:bg-teal-500/10 dark:text-teal-300 dark:ring-teal-500/30' },
  { pattern: /практи|семинар|practice|seminar/i, label: 'Практика', badge: 'bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-300 dark:ring-emerald-500/30' },
]

const DEFAULT_TYPE = {
  label: 'Задание',
  badge: 'bg-slate-100 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
}

export function getTaskType(task) {
  const text = `${task.title ?? ''} ${task.description ?? ''}`
  return TASK_TYPES.find((type) => type.pattern.test(text)) ?? DEFAULT_TYPE
}

const DAY_MS = 24 * 60 * 60 * 1000

const TONES = {
  overdue: 'bg-red-50 text-red-700 ring-red-200 dark:bg-red-500/10 dark:text-red-300 dark:ring-red-500/30',
  soon: 'bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:ring-amber-500/30',
  later: 'bg-slate-100 text-slate-700 ring-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-700',
  none: 'bg-slate-50 text-slate-500 ring-slate-200 dark:bg-slate-800/50 dark:text-slate-400 dark:ring-slate-700',
}

/**
 * Deadline tag: "просрочено" (unless completed), "через N дн." within three days, otherwise the date.
 * The backend sends local date-time without a zone, which Date parses as local time, as intended.
 */
export function getDeadlineInfo(deadline, status, now = Date.now()) {
  if (!deadline) return { label: 'Без срока', tone: TONES.none }
  const date = new Date(deadline)
  const formatted = formatDateTime(deadline)
  const diff = date.getTime() - now
  if (status === 'COMPLETED') return { label: formatted, tone: TONES.later }
  if (diff < 0) return { label: `Просрочено · ${formatted}`, tone: TONES.overdue }
  if (diff < DAY_MS) return { label: `Сегодня · ${formatted}`, tone: TONES.soon }
  if (diff < 3 * DAY_MS) return { label: `Через ${Math.ceil(diff / DAY_MS)} дн. · ${formatted}`, tone: TONES.soon }
  return { label: formatted, tone: TONES.later }
}
