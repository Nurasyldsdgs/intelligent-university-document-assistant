import { ShieldAlert } from 'lucide-react'
import { managesDocument, piiTypesText } from '../utils/privacy'

/**
 * "Личные данные: N" for the course teacher and ADMIN; the kinds (ИИН, телефон, e-mail, номер документа) are in the
 * tooltip and read out by screen readers. Nothing for students, or when the text has no personal data.
 */
export default function PiiBadge({ doc, size = 'small' }) {
  if (!doc?.piiCount || !managesDocument(doc)) return null
  const types = piiTypesText(doc.piiTypes)
  const hint = `Скрыто в тексте для студентов, ИИ, поиска и ассистента${types ? `: ${types}` : ''}`
  const sizing = size === 'large' ? 'gap-1 rounded-md px-2 py-0.5 text-xs' : 'gap-0.5 rounded px-1.5 py-0.5 text-[10px]'
  return (
    <span
      title={hint}
      className={`inline-flex items-center font-semibold ring-1 ring-inset bg-violet-50 dark:bg-violet-500/10 text-violet-700 dark:text-violet-300 ring-violet-200 dark:ring-violet-500/30 ${sizing}`}
    >
      <ShieldAlert className={size === 'large' ? 'h-3 w-3' : 'h-2.5 w-2.5'} aria-hidden="true" />
      Личные данные: {doc.piiCount}
      {types && <span className="sr-only"> ({types})</span>}
    </span>
  )
}
