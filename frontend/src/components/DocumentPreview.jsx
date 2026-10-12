import { useEffect, useState } from 'react'
import { CalendarClock, HardDrive, Loader2, User } from 'lucide-react'
import api, { getBlobErrorMessage } from '../api/axios'
import { dateFormatter, formatSize, getFileFormat, isImage, isPreviewable } from '../utils/fileTypes'

/** Inline preview for PDF and images (GET /documents/{id}/view); other types get a metadata card. */
export default function DocumentPreview({ doc }) {
  const format = getFileFormat(doc)
  const previewable = isPreviewable(doc)
  // Only fetched for previewable types; otherwise the metadata card is shown straight away.
  const [preview, setPreview] = useState({ loading: previewable, url: null, error: '' })

  useEffect(() => {
    if (!previewable) return undefined
    let objectUrl = null
    let cancelled = false

    api.get(`/documents/${doc.id}/view`, { responseType: 'blob', timeout: 0 })
      .then(({ data }) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(data)
        setPreview({ loading: false, url: objectUrl, error: '' })
      })
      .catch(async (err) => {
        const message = await getBlobErrorMessage(err)
        if (!cancelled) setPreview({ loading: false, url: null, error: message })
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [doc.id, previewable])

  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50">
      {preview.loading ? (
        <div className="flex h-72 items-center justify-center gap-2 text-sm text-slate-500 dark:text-slate-400">
          <Loader2 className="h-4 w-4 animate-spin" /> Загрузка предпросмотра…
        </div>
      ) : preview.url && isImage(doc) ? (
        <div className="flex max-h-[70vh] items-center justify-center p-4">
          <img src={preview.url} alt={doc.originalFileName} className="max-h-[66vh] max-w-full rounded-lg object-contain shadow-sm" />
        </div>
      ) : preview.url ? (
        <iframe src={preview.url} title={doc.originalFileName} className="h-[70vh] w-full bg-white dark:bg-slate-900" />
      ) : (
        <MetadataCard doc={doc} format={format} error={preview.error} />
      )}
    </div>
  )
}

function MetadataCard({ doc, format, error }) {
  const FormatIcon = format.icon
  return (
    <div className="p-6">
      <div className="flex flex-col items-center text-center">
        <span className={`flex h-14 w-14 items-center justify-center rounded-2xl ${format.tile}`}>
          <FormatIcon className="h-7 w-7" />
        </span>
        <p className="mt-3 text-sm font-medium text-slate-700 dark:text-slate-200">
          {error || 'Предпросмотр доступен для PDF и изображений'}
        </p>
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
          Скачайте файл или задайте вопрос по его тексту во вкладке «Чат с ИИ».
        </p>
      </div>
      <dl className="mx-auto mt-6 grid max-w-2xl gap-3 sm:grid-cols-3">
        <MetaItem icon={HardDrive} label="Размер" value={formatSize(doc.fileSize)} />
        <MetaItem icon={CalendarClock} label="Дата загрузки" value={dateFormatter.format(new Date(doc.uploadedAt))} />
        <MetaItem icon={User} label="Владелец" value={doc.ownerFullName || doc.ownerEmail} title={doc.ownerEmail} />
      </dl>
    </div>
  )
}

function MetaItem({ icon: Icon, label, value, title }) {
  return (
    <div className="rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-3">
      <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-400 dark:text-slate-500">
        <Icon className="h-3.5 w-3.5" />
        {label}
      </dt>
      <dd className="mt-1 truncate text-sm font-medium text-slate-900 dark:text-white" title={title ?? value}>{value}</dd>
    </div>
  )
}
