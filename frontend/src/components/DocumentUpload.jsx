import { useEffect, useRef, useState } from 'react'
import { Archive, Bot, CheckCircle2, FileText, Loader2, Sparkles, UploadCloud, X, XCircle } from 'lucide-react'
import api, { getErrorMessage } from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { ROLE_STUDENT } from '../constants/roles'
import { AUTO_ANALYZED_CATEGORIES, CATEGORY_LABELS, CATEGORY_OPTIONS, DOCUMENT_EXTENSIONS, VISIBILITY_OPTIONS } from '../constants/documents'
import { formatSize } from '../utils/fileTypes'

// Must match DocumentService.MAX_DOCUMENT_BYTES and spring.servlet.multipart on the backend.
const MAX_FILE_SIZE = 20 * 1024 * 1024
const MAX_ARCHIVE_SIZE = 50 * 1024 * 1024
const MAX_REQUEST_SIZE = 90 * 1024 * 1024 // under max-request-size (100 MB), leaving room for the form fields
const MAX_FILES_PER_REQUEST = 50
const AUTO = '' // category value: let the AI classify each file

const DOCUMENT_ACCEPT = DOCUMENT_EXTENSIONS.map((ext) => `.${ext}`).join(',')

const selectClass = 'w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-sm text-slate-900 dark:text-white outline-none transition focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100 dark:focus:ring-indigo-500/30 disabled:opacity-60'

const extensionOf = (name) => name.split('.').pop().toLowerCase()
const isZip = (file) => extensionOf(file.name) === 'zip'

// Groups files so that one request stays under the backend's request size and file count limits.
function intoRequests(files) {
  const groups = []
  let current = []
  let size = 0
  for (const file of files) {
    if (current.length && (size + file.size > MAX_REQUEST_SIZE || current.length === MAX_FILES_PER_REQUEST)) {
      groups.push(current)
      current = []
      size = 0
    }
    current.push(file)
    size += file.size
  }
  if (current.length) groups.push(current)
  return groups
}

/**
 * Multi-file drag & drop upload into a course (US-04), with ZIP import (US-06) and AI type classification (US-05).
 * For the course teacher or ADMIN only: callers do not render it for students, and the ZIP option is hidden for
 * them as well. Every document belongs to a course: with {@code course} it is fixed (course page), otherwise one of
 * the user's courses must be picked.
 */
export default function DocumentUpload({ course, onUploaded }) {
  const { role } = useAuth()
  const { notifyError, showError, showSuccess } = useToast()
  const canImportZip = role !== ROLE_STUDENT
  const inputRef = useRef(null)
  const zipInputRef = useRef(null)

  const [category, setCategory] = useState(AUTO)
  const [visibility, setVisibility] = useState('COURSE_STUDENTS')
  const [courseId, setCourseId] = useState(course ? String(course.id) : '')
  const [courses, setCourses] = useState(course ? [course] : [])
  // While sending: { label, progress, phase: 'upload' | 'processing' }.
  const [upload, setUpload] = useState(null)
  const [result, setResult] = useState(null) // { uploaded: [...], skipped: [...] } of the last upload
  const [dragActive, setDragActive] = useState(false)

  useEffect(() => {
    if (course) return undefined
    let cancelled = false
    api.get('/courses', { params: { size: 100 } })
      .then(({ data }) => !cancelled && setCourses(data.content))
      .catch(() => {}) // The upload still works without a course list.
    return () => {
      cancelled = true
    }
  }, [course])

  const send = async (url, formData, label) => {
    setUpload({ label, progress: 0, phase: 'upload' })
    const { data } = await api.post(url, formData, {
      // Lets axios send FormData as-is; the browser adds the multipart boundary itself.
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 0,
      onUploadProgress: ({ loaded, total }) => {
        if (!total) return
        const progress = Math.round((loaded / total) * 100)
        // Once the bytes are sent, the server extracts text and asks the AI for each file's type.
        setUpload({ label, progress, phase: progress >= 100 ? 'processing' : 'upload' })
      },
    })
    return data
  }

  const formFor = () => {
    const formData = new FormData()
    if (category !== AUTO) formData.append('category', category)
    formData.append('visibility', visibility)
    formData.append('courseId', courseId)
    return formData
  }

  const uploadFiles = async (fileList) => {
    const all = Array.from(fileList ?? [])
    if (all.length === 0 || upload) return
    if (!courseId) {
      showError('Выберите курс: каждый документ принадлежит курсу')
      return
    }

    const skipped = []
    const archives = []
    const documents = []
    for (const file of all) {
      if (isZip(file)) {
        if (!canImportZip) skipped.push({ fileName: file.name, reason: 'Загружать ZIP-архивы может только преподаватель курса' })
        else if (file.size > MAX_ARCHIVE_SIZE) skipped.push({ fileName: file.name, reason: `Архив больше ${formatSize(MAX_ARCHIVE_SIZE)}` })
        else archives.push(file)
      } else if (!DOCUMENT_EXTENSIONS.includes(extensionOf(file.name))) {
        skipped.push({ fileName: file.name, reason: 'Неподдерживаемый формат файла' })
      } else if (file.size > MAX_FILE_SIZE) {
        skipped.push({ fileName: file.name, reason: `Файл больше ${formatSize(MAX_FILE_SIZE)}` })
      } else {
        documents.push(file)
      }
    }

    const uploaded = []
    let current = 'Загрузка' // label of the request in flight, for the error line
    setResult(null)
    try {
      for (const group of intoRequests(documents)) {
        const formData = formFor()
        group.forEach((file) => formData.append('files', file))
        current = group.length === 1 ? `«${group[0].name}»` : `${group.length} файлов`
        const data = await send('/documents/upload-multiple', formData, current)
        uploaded.push(...data.uploaded)
        skipped.push(...data.skipped)
      }
      for (const archive of archives) {
        const formData = formFor()
        formData.append('file', archive)
        current = `архива «${archive.name}»`
        const data = await send('/documents/upload-batch', formData, current)
        uploaded.push(...data.uploaded)
        skipped.push(...data.skipped.map((item) => ({ ...item, fileName: `${archive.name} → ${item.fileName}` })))
      }
    } catch (err) {
      // What was sent before the failure stays uploaded; the failed request is shown with its reason.
      notifyError(err)
      skipped.push({ fileName: current, reason: getErrorMessage(err) })
    } finally {
      setUpload(null)
    }

    setResult({ uploaded, skipped })
    if (uploaded.length > 0) {
      const analyzed = uploaded.some(({ document }) => AUTO_ANALYZED_CATEGORIES.has(document.category) && document.hasExtractedText)
      showSuccess(`Загружено документов: ${uploaded.length}.${analyzed ? ' ИИ извлекает дедлайны из силлабусов и заданий — они появятся через несколько секунд.' : ''}`)
      onUploaded?.(uploaded.map(({ document }) => document))
    }
  }

  const busy = Boolean(upload)

  return (
    <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 shadow-sm sm:p-5">
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="Тип документа" htmlFor="upload-category">
          <select id="upload-category" value={category} onChange={(event) => setCategory(event.target.value)} className={selectClass} disabled={busy}>
            <option value={AUTO}>Определить автоматически (ИИ)</option>
            {CATEGORY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </Field>
        <Field label="Кто видит" htmlFor="upload-visibility">
          <select id="upload-visibility" value={visibility} onChange={(event) => setVisibility(event.target.value)} className={selectClass} disabled={busy}>
            {VISIBILITY_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
          </select>
        </Field>
        <Field label="Курс" htmlFor="upload-course">
          <select
            id="upload-course"
            value={courseId}
            onChange={(event) => setCourseId(event.target.value)}
            className={`${selectClass} ${!courseId ? 'border-amber-400' : ''}`}
            disabled={Boolean(course) || busy}
          >
            {!course && <option value="">{courses.length ? 'Выберите курс' : 'Нет ваших курсов'}</option>}
            {courses.map((item) => (
              <option key={item.id} value={item.id}>{item.code ? `${item.code} · ${item.title}` : item.title}</option>
            ))}
          </select>
        </Field>
      </div>
      <p className="mt-2 flex items-center gap-1.5 text-xs text-violet-700 dark:text-violet-300">
        <Sparkles className="h-3.5 w-3.5 shrink-0" />
        {category === AUTO
          ? 'ИИ определит тип каждого файла (силлабус, задание, лекция, транскрипт…) и извлечёт дедлайны из силлабусов и заданий.'
          : AUTO_ANALYZED_CATEGORIES.has(category)
            ? 'Силлабусы и задания ИИ анализирует автоматически: краткая суть, темы и дедлайны.'
            : 'Тип задан вручную — ИИ-классификация не используется.'}
      </p>

      <div
        role="button"
        tabIndex={0}
        aria-disabled={busy}
        onClick={() => !busy && inputRef.current?.click()}
        onKeyDown={(event) => {
          if ((event.key === 'Enter' || event.key === ' ') && !busy) {
            event.preventDefault()
            inputRef.current?.click()
          }
        }}
        onDragOver={(event) => {
          event.preventDefault()
          if (!busy) setDragActive(true)
        }}
        onDragLeave={() => setDragActive(false)}
        onDrop={(event) => {
          event.preventDefault()
          setDragActive(false)
          uploadFiles(event.dataTransfer.files)
        }}
        className={`mt-4 flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed p-7 text-center transition-all duration-200 focus:outline-none focus:ring-4 focus:ring-indigo-100 dark:focus:ring-indigo-500/30 ${
          busy
            ? 'cursor-wait border-indigo-300 dark:border-indigo-500/30 bg-indigo-50/50 dark:bg-indigo-500/10'
            : dragActive
              ? 'cursor-copy border-indigo-500 bg-indigo-50/70 dark:bg-indigo-500/10'
              : 'border-indigo-200 dark:border-indigo-500/30 bg-indigo-50/30 dark:bg-indigo-500/5 hover:border-indigo-500 hover:bg-indigo-50/70 dark:hover:bg-indigo-500/10'
        }`}
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          accept={canImportZip ? `${DOCUMENT_ACCEPT},.zip` : DOCUMENT_ACCEPT}
          className="hidden"
          onChange={(event) => {
            uploadFiles(event.target.files)
            event.target.value = ''
          }}
        />
        {upload ? <UploadProgress upload={upload} classifying={category === AUTO} /> : (
          <>
            <div className="flex items-center gap-2 text-indigo-500">
              <FileText className="h-6 w-6" />
              <UploadCloud className={`h-10 w-10 ${dragActive ? 'text-indigo-600 dark:text-indigo-400' : 'text-indigo-400'}`} />
              {canImportZip && <Archive className="h-6 w-6" />}
            </div>
            <p className="mt-3 text-sm font-medium text-slate-700 dark:text-slate-200">
              Перетащите сюда несколько файлов{canImportZip ? ' или ZIP-архив' : ''}, или{' '}
              <span className="text-indigo-600 dark:text-indigo-400 underline-offset-2 hover:underline">выберите файлы</span>
            </p>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              PDF, DOCX, PPTX, TXT, PNG, JPG — до {formatSize(MAX_FILE_SIZE)} на файл{canImportZip ? `; ZIP — до ${formatSize(MAX_ARCHIVE_SIZE)}` : ''}
            </p>
          </>
        )}
      </div>

      {canImportZip && (
        <div className="mt-3 flex justify-end">
          <input
            ref={zipInputRef}
            type="file"
            accept=".zip,application/zip"
            className="hidden"
            onChange={(event) => {
              uploadFiles(event.target.files)
              event.target.value = ''
            }}
          />
          <button
            type="button"
            onClick={() => zipInputRef.current?.click()}
            disabled={busy}
            className="flex items-center gap-2 rounded-lg border border-slate-300 dark:border-slate-700 px-3 py-1.5 text-xs font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60"
          >
            <Archive className="h-4 w-4" /> Загрузить ZIP-архив
          </button>
        </div>
      )}

      {result && <UploadResult result={result} onClose={() => setResult(null)} />}
    </section>
  )
}

function UploadProgress({ upload, classifying }) {
  const processing = upload.phase === 'processing'
  return (
    <div className="w-full max-w-sm">
      {processing && classifying
        ? <Bot className="mx-auto h-8 w-8 animate-pulse text-violet-600 dark:text-violet-400" />
        : <Loader2 className="mx-auto h-8 w-8 animate-spin text-indigo-600 dark:text-indigo-400" />}
      <p className="mt-3 truncate text-sm font-medium text-slate-700 dark:text-slate-200">
        {processing
          ? (classifying ? `ИИ определяет тип ${upload.label}…` : `Распознавание текста ${upload.label}…`)
          : `Загрузка ${upload.label}…`}
      </p>
      <div className="mt-3 h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
        <div
          className={`h-full rounded-full transition-all duration-200 ${processing ? 'animate-pulse bg-violet-500' : 'bg-indigo-600'}`}
          style={{ width: `${upload.progress}%` }}
        />
      </div>
      <p className="mt-1 flex items-center justify-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
        {processing ? <><Loader2 className="h-3 w-3 animate-spin" /> Обработка на сервере</> : `${upload.progress}%`}
      </p>
    </div>
  )
}

function UploadResult({ result, onClose }) {
  return (
    <div className="mt-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/60 dark:bg-slate-800/40 p-3">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-semibold text-slate-700 dark:text-slate-200">
          Загружено: {result.uploaded.length}{result.skipped.length > 0 && ` · пропущено: ${result.skipped.length}`}
        </p>
        <button type="button" onClick={onClose} className="rounded p-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200" aria-label="Скрыть результат">
          <X className="h-4 w-4" />
        </button>
      </div>
      <ul className="mt-2 max-h-56 space-y-1 overflow-y-auto">
        {result.uploaded.map(({ document, aiClassified }) => (
          <li key={document.id} className="flex items-center gap-2 text-xs">
            <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-emerald-600" />
            <span className="min-w-0 flex-1 truncate text-slate-700 dark:text-slate-200" title={document.originalFileName}>{document.originalFileName}</span>
            <span className="inline-flex shrink-0 items-center gap-1 rounded-md bg-white dark:bg-slate-900 px-1.5 py-0.5 font-semibold text-slate-600 dark:text-slate-300 ring-1 ring-inset ring-slate-200 dark:ring-slate-700">
              {aiClassified && <Bot className="h-3 w-3 text-violet-600 dark:text-violet-400" aria-label="Тип определён ИИ" />}
              {CATEGORY_LABELS[document.category] ?? document.category}
            </span>
          </li>
        ))}
        {result.skipped.map((item, index) => (
          <li key={`skipped-${index}`} className="flex items-start gap-2 text-xs">
            <XCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-red-500" />
            <span className="min-w-0 flex-1 truncate text-slate-500 dark:text-slate-400" title={item.fileName}>{item.fileName}</span>
            <span className="shrink-0 text-red-600 dark:text-red-400">{item.reason}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Field({ label, htmlFor, children }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300">{label}</label>
      {children}
    </div>
  )
}
