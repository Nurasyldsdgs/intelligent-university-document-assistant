import { File, FileImage, FileSpreadsheet, FileText } from 'lucide-react'

// Must match INLINE_CONTENT_TYPES in DocumentController: only these are served by /documents/{id}/view.
const PREVIEWABLE_TYPES = new Set(['application/pdf', 'image/png', 'image/jpeg', 'image/gif', 'image/webp'])

const FORMATS = {
  pdf: { label: 'PDF', icon: FileText, badge: 'bg-red-50 text-red-700 ring-red-200', tile: 'bg-red-50 text-red-600' },
  doc: { label: 'DOCX', icon: FileText, badge: 'bg-blue-50 text-blue-700 ring-blue-200', tile: 'bg-blue-50 text-blue-600' },
  sheet: { label: 'XLSX', icon: FileSpreadsheet, badge: 'bg-emerald-50 text-emerald-700 ring-emerald-200', tile: 'bg-emerald-50 text-emerald-600' },
  image: { label: 'IMG', icon: FileImage, badge: 'bg-violet-50 text-violet-700 ring-violet-200', tile: 'bg-violet-50 text-violet-600' },
}

const OTHER_STYLE = { icon: File, badge: 'bg-slate-100 text-slate-600 ring-slate-200', tile: 'bg-slate-100 text-slate-500' }

function baseContentType(contentType) {
  return (contentType ?? '').split(';')[0].trim().toLowerCase()
}

/** Format badge info from the MIME type, falling back to the file extension. */
export function getFileFormat(doc) {
  const type = baseContentType(doc.contentType)
  const ext = doc.originalFileName?.includes('.') ? doc.originalFileName.split('.').pop().toLowerCase() : ''

  if (type === 'application/pdf' || ext === 'pdf') return FORMATS.pdf
  if (type.startsWith('image/')) return FORMATS.image
  if (type.includes('wordprocessingml') || type === 'application/msword' || ext === 'docx' || ext === 'doc') {
    return { ...FORMATS.doc, label: ext === 'doc' ? 'DOC' : 'DOCX' }
  }
  if (type.includes('spreadsheetml') || type === 'application/vnd.ms-excel' || ext === 'xlsx' || ext === 'xls') {
    return { ...FORMATS.sheet, label: ext === 'xls' ? 'XLS' : 'XLSX' }
  }
  return { ...OTHER_STYLE, label: ext ? ext.toUpperCase().slice(0, 5) : 'FILE' }
}

export function isPreviewable(doc) {
  return PREVIEWABLE_TYPES.has(baseContentType(doc.contentType))
}

// The backend decides: the course's teacher or ADMIN. Uploading a document does not make it the uploader's to delete.
export function canDeleteDocument(doc) {
  return Boolean(doc.canDelete)
}

export function isImage(doc) {
  return baseContentType(doc.contentType).startsWith('image/')
}

export function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} Б`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} КБ`
  return `${(bytes / 1024 / 1024).toFixed(1)} МБ`
}
