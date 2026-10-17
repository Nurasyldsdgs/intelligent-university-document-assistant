import { AlertTriangle, ScanText } from 'lucide-react'
import { managesDocument } from '../utils/privacy'

const tag = 'inline-flex items-center font-semibold ring-1 ring-inset'

/**
 * US-07, text recognition of a scan. The course teacher and ADMIN see "Нужна проверка" when it failed; everyone
 * sees "Распознано со скана" (the text may contain OCR errors). While it runs, DocumentAiBadge shows the progress.
 */
export default function OcrBadge({ doc, size = 'small' }) {
  const sizing = size === 'large' ? 'gap-1 rounded-md px-2 py-0.5 text-xs' : 'gap-0.5 rounded px-1.5 py-0.5 text-[10px]'
  const icon = size === 'large' ? 'h-3 w-3' : 'h-2.5 w-2.5'
  const manager = managesDocument(doc)

  if (manager && doc?.ocrStatus === 'FAILED') {
    return (
      <span
        title="Не удалось распознать текст скана. Проверьте файл или загрузите версию с текстовым слоем."
        className={`${tag} ${sizing} bg-amber-50 dark:bg-amber-500/10 text-amber-800 dark:text-amber-300 ring-amber-200 dark:ring-amber-500/30`}
      >
        <AlertTriangle className={icon} aria-hidden="true" /> Нужна проверка
      </span>
    )
  }
  if (doc?.textFromOcr) {
    return (
      <span
        title="Текст получен распознаванием скана и может содержать ошибки"
        className={`${tag} ${sizing} bg-sky-50 dark:bg-sky-500/10 text-sky-700 dark:text-sky-300 ring-sky-200 dark:ring-sky-500/30`}
      >
        <ScanText className={icon} aria-hidden="true" /> Распознано со скана
      </span>
    )
  }
  return null
}
