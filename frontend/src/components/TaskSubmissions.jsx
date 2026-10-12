import { useRef, useState } from 'react'
import { ChevronDown, Download, Loader2, Paperclip, UploadCloud } from 'lucide-react'
import api, { getBlobErrorMessage } from '../api/axios'
import { useToast } from '../context/ToastContext'
import { DOCUMENT_EXTENSIONS } from '../constants/documents'
import { formatSize } from '../utils/fileTypes'

// Must match SubmissionService.MAX_FILES_PER_SUBMISSION and DocumentService.MAX_DOCUMENT_BYTES.
const MAX_FILES = 10
const MAX_FILE_SIZE = 20 * 1024 * 1024
const ACCEPT = DOCUMENT_EXTENSIONS.map((ext) => `.${ext}`).join(',')

const extensionOf = (name) => name.split('.').pop().toLowerCase()

/**
 * Submissions of a task (Moodle-style). With {@code taskId} it is the student's own task: files are dropped or picked
 * here and handed in with POST /tasks/{id}/submissions - the only upload a student can make. With
 * {@code assignmentId} it is the course teacher's read-only view of every student's submissions.
 */
export default function TaskSubmissions({ taskId, assignmentId, count = 0 }) {
  const { notifyError, showError, showSuccess } = useToast()
  const inputRef = useRef(null)
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState(null)
  // The teacher's view learns the number only when it is opened.
  const [total, setTotal] = useState(taskId ? count : null)
  const [sending, setSending] = useState(false)
  const [dragActive, setDragActive] = useState(false)
  const own = Boolean(taskId)
  const listUrl = own ? `/tasks/${taskId}/submissions` : `/assignments/${assignmentId}/submissions`

  const load = async () => {
    try {
      const { data } = await api.get(listUrl)
      setItems(data)
      setTotal(data.length)
    } catch (err) {
      notifyError(err)
    }
  }

  const toggle = () => {
    if (!open && items === null) load()
    setOpen(!open)
  }

  const submit = async (fileList) => {
    const files = Array.from(fileList ?? [])
    if (files.length === 0 || sending) return
    const problem = files.length > MAX_FILES ? `Можно приложить не больше ${MAX_FILES} файлов`
      : files.map((file) => (!DOCUMENT_EXTENSIONS.includes(extensionOf(file.name)) ? `«${file.name}»: неподдерживаемый формат`
        : file.size > MAX_FILE_SIZE ? `«${file.name}» больше ${formatSize(MAX_FILE_SIZE)}` : null)).find(Boolean)
    if (problem) {
      showError(problem)
      return
    }
    const formData = new FormData()
    files.forEach((file) => formData.append('files', file))
    setSending(true)
    try {
      await api.post(listUrl, formData, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 0 })
      showSuccess(files.length === 1 ? 'Решение отправлено' : `Отправлено файлов: ${files.length}`)
      setOpen(true)
      await load()
    } catch (err) {
      notifyError(err)
    } finally {
      setSending(false)
    }
  }

  const download = async (item) => {
    try {
      const { data } = await api.get(`/submissions/${item.id}/download`, { responseType: 'blob', timeout: 0 })
      const url = URL.createObjectURL(data)
      const link = document.createElement('a')
      link.href = url
      link.download = item.originalFileName
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      showError(await getBlobErrorMessage(err))
    }
  }

  return (
    <div className="mt-2">
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={toggle}
          className="inline-flex items-center gap-1 text-[11px] font-medium text-slate-600 dark:text-slate-300 hover:text-indigo-600 dark:hover:text-indigo-400"
          aria-expanded={open}
        >
          <Paperclip className="h-3 w-3" />
          {own ? 'Моё решение' : 'Решения студентов'}{total !== null && `: ${total}`}
          <ChevronDown className={`h-3 w-3 transition ${open ? 'rotate-180' : ''}`} />
        </button>
        {own && (
          <>
            <input
              ref={inputRef}
              type="file"
              multiple
              accept={ACCEPT}
              className="hidden"
              onChange={(event) => {
                submit(event.target.files)
                event.target.value = ''
              }}
            />
            <button
              type="button"
              disabled={sending}
              onClick={() => inputRef.current?.click()}
              onDragOver={(event) => {
                event.preventDefault()
                if (!sending) setDragActive(true)
              }}
              onDragLeave={() => setDragActive(false)}
              onDrop={(event) => {
                event.preventDefault()
                setDragActive(false)
                submit(event.dataTransfer.files)
              }}
              className={`inline-flex items-center gap-1 rounded-md border border-dashed px-2 py-0.5 text-[11px] font-medium transition disabled:opacity-60 ${
                dragActive
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-700 dark:bg-indigo-500/10 dark:text-indigo-300'
                  : 'border-indigo-300 text-indigo-600 hover:bg-indigo-50 dark:border-indigo-500/40 dark:text-indigo-400 dark:hover:bg-indigo-500/10'
              }`}
              title="Перетащите файлы сюда или нажмите, чтобы выбрать"
            >
              {sending ? <Loader2 className="h-3 w-3 animate-spin" /> : <UploadCloud className="h-3 w-3" />}
              {sending ? 'Отправка…' : 'Сдать решение'}
            </button>
          </>
        )}
      </div>

      {open && (
        items === null ? (
          <p className="mt-1.5 flex items-center gap-1.5 text-[11px] text-slate-500"><Loader2 className="h-3 w-3 animate-spin" /> Загрузка…</p>
        ) : items.length === 0 ? (
          <p className="mt-1.5 text-[11px] text-slate-500 dark:text-slate-400">{own ? 'Вы ещё ничего не сдали.' : 'Пока никто не сдал решение.'}</p>
        ) : (
          <ul className="mt-1.5 space-y-1">
            {items.map((item) => (
              <li key={item.id} className="flex items-center gap-2 text-[11px]">
                {!own && (
                  <span className="max-w-40 shrink-0 truncate font-medium text-slate-700 dark:text-slate-200" title={item.studentEmail}>
                    {item.studentFullName || item.studentEmail}
                  </span>
                )}
                <span className="min-w-0 flex-1 truncate text-slate-600 dark:text-slate-300" title={item.originalFileName}>{item.originalFileName}</span>
                <span className="shrink-0 text-slate-400">{formatSize(item.fileSize)} · {new Date(item.submittedAt).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })}</span>
                <button type="button" onClick={() => download(item)} className="shrink-0 rounded p-0.5 text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400" aria-label={`Скачать ${item.originalFileName}`}>
                  <Download className="h-3.5 w-3.5" />
                </button>
              </li>
            ))}
          </ul>
        )
      )}
    </div>
  )
}
