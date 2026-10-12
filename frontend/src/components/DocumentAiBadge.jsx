import { Loader2 } from 'lucide-react'
import CategoryBadge from './CategoryBadge'

/**
 * A document's category as the AI left it: a spinner while it is being classified (only teachers and ADMIN receive
 * such documents), "Тип не определён" when the AI failed, otherwise the category - with the AI's confidence when
 * the AI chose it.
 */
export default function DocumentAiBadge({ doc }) {
  if (doc.aiStatus === 'PENDING' || doc.aiStatus === 'PROCESSING') {
    return (
      <span className="inline-flex shrink-0 items-center gap-1 text-[11px] font-medium text-violet-700 dark:text-violet-300" title="Студенты увидят документ после классификации">
        <Loader2 className="h-3 w-3 animate-spin" /> ИИ определяет тип…
      </span>
    )
  }
  if (doc.aiStatus === 'FAILED') {
    return (
      <span
        className="inline-flex shrink-0 items-center rounded-md bg-amber-50 px-1.5 py-0.5 text-[11px] font-semibold text-amber-800 ring-1 ring-inset ring-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:ring-amber-500/30"
        title="ИИ не смог определить тип документа"
      >
        Тип не определён
      </span>
    )
  }
  const byAi = doc.aiStatus === 'DONE'
  return <CategoryBadge category={doc.category} confidence={byAi ? doc.aiConfidence : null} ai={byAi} />
}
