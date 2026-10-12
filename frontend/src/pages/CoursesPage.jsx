import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, BookOpen, GraduationCap, Loader2, Plus, Search } from 'lucide-react'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { ROLE_ADMIN, ROLE_STUDENT } from '../constants/roles'
import Navbar from '../components/Navbar'

const PAGE_SIZE = 12
const inputClass = 'w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-sm text-slate-900 dark:text-white outline-none transition placeholder:text-slate-500 dark:placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100 dark:focus:ring-indigo-500/30'

/** "My courses": enrolled ones for a student, taught ones for a teacher, all for ADMIN (GET /courses). */
export default function CoursesPage() {
  const { role } = useAuth()
  const { notifyError } = useToast()
  const isStudent = role === ROLE_STUDENT
  const [page, setPage] = useState(null)
  const [loading, setLoading] = useState(true)

  const fetchPage = useCallback((number) => api.get('/courses', { params: { page: number, size: PAGE_SIZE } })
    .then(({ data }) => data), [])

  useEffect(() => {
    let cancelled = false
    fetchPage(0)
      .then((data) => !cancelled && setPage(data))
      .catch((err) => !cancelled && notifyError(err))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [fetchPage, notifyError])

  const loadMore = async () => {
    setLoading(true)
    try {
      const next = await fetchPage(page.page + 1)
      setPage({ ...next, content: [...page.content, ...next.content] })
    } catch (err) {
      notifyError(err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-50/50 dark:bg-slate-950">
      <Navbar />
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-100 dark:bg-indigo-500/20 text-indigo-700 dark:text-indigo-300">
            <GraduationCap className="h-5 w-5" />
          </span>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Курсы</h1>
            <p className="text-sm text-slate-500 dark:text-slate-400">
              {isStudent ? 'Курсы, на которые вы записаны' : role === ROLE_ADMIN ? 'Все курсы системы' : 'Курсы, которые вы ведёте'}
            </p>
          </div>
        </div>

        <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <section>
            {!page && loading ? (
              <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500 dark:text-slate-400">
                <Loader2 className="h-4 w-4 animate-spin" /> Загрузка курсов…
              </div>
            ) : page?.content.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-6 py-14 text-center">
                <BookOpen className="mx-auto h-8 w-8 text-slate-400" />
                <p className="mt-3 text-sm font-medium text-slate-700 dark:text-slate-200">Курсов пока нет</p>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  {isStudent ? 'Попросите преподавателя прислать ссылку-приглашение и откройте её или вставьте в форму справа.' : 'Создайте первый курс в форме справа.'}
                </p>
              </div>
            ) : page && (
              <>
                <ul className="grid gap-3 sm:grid-cols-2">
                  {page.content.map((course) => <CourseCard key={course.id} course={course} showNumber={role === ROLE_ADMIN} />)}
                </ul>
                {!page.last && (
                  <button
                    type="button"
                    onClick={loadMore}
                    disabled={loading}
                    className="mt-3 flex w-full items-center justify-center gap-2 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 transition hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60"
                  >
                    {loading && <Loader2 className="h-4 w-4 animate-spin" />} Показать ещё
                  </button>
                )}
              </>
            )}
          </section>

          <aside className="space-y-4">
            {isStudent ? <JoinByLinkForm /> : <CreateCourseForm />}
          </aside>
        </div>
      </main>
    </div>
  )
}

function CourseCard({ course, showNumber }) {
  return (
    <li>
      <Link
        to={`/courses/${course.id}`}
        className="group flex h-full flex-col rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 shadow-sm transition-all hover:-translate-y-0.5 hover:border-indigo-200 dark:hover:border-indigo-500/30 hover:shadow-md"
      >
        <span className="flex items-center gap-2">
          {course.code && (
            <span className="rounded-md bg-indigo-50 dark:bg-indigo-500/10 px-1.5 py-0.5 text-xs font-bold text-indigo-700 dark:text-indigo-300">{course.code}</span>
          )}
          {showNumber && <span className="text-xs text-slate-500 dark:text-slate-400">№ {course.id}</span>}
        </span>
        <span className="mt-2 font-semibold text-slate-900 dark:text-white">{course.title}</span>
        {course.description && <span className="mt-1 line-clamp-2 text-xs text-slate-500 dark:text-slate-400">{course.description}</span>}
        <span className="mt-auto flex items-center justify-between pt-3 text-xs text-slate-500 dark:text-slate-400">
          {course.teacherFullName || course.teacherEmail || 'Преподаватель не назначен'}
          <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1 group-hover:text-indigo-600" />
        </span>
      </Link>
    </li>
  )
}

function CreateCourseForm() {
  const navigate = useNavigate()
  const { notifyError, showSuccess } = useToast()
  const [form, setForm] = useState({ title: '', code: '', description: '' })
  const [saving, setSaving] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setSaving(true)
    try {
      const { data } = await api.post('/courses', {
        title: form.title.trim(),
        code: form.code.trim() || null,
        description: form.description.trim() || null,
      })
      showSuccess(`Курс «${data.title}» создан`)
      navigate(`/courses/${data.id}`)
    } catch (err) {
      notifyError(err)
      setSaving(false)
    }
  }

  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }))

  return (
    <form onSubmit={submit} className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 shadow-sm">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-white">
        <Plus className="h-4 w-4 text-indigo-600" /> Новый курс
      </h2>
      <div className="mt-3 space-y-3">
        <input value={form.title} onChange={update('title')} required maxLength={255} placeholder="Название, например «Алгоритмы»" className={inputClass} aria-label="Название курса" />
        <input value={form.code} onChange={update('code')} maxLength={50} placeholder="Код, например CS101" className={inputClass} aria-label="Код курса" />
        <textarea value={form.description} onChange={update('description')} rows={3} maxLength={10000} placeholder="Описание (необязательно)" className={`${inputClass} resize-none`} aria-label="Описание курса" />
        <button
          type="submit"
          disabled={saving || !form.title.trim()}
          className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:opacity-60"
        >
          {saving && <Loader2 className="h-4 w-4 animate-spin" />} Создать курс
        </button>
      </div>
    </form>
  )
}

// The code of an invitation link: the part after /join/ of a pasted link, or the code itself.
function inviteCodeFrom(input) {
  const value = input.trim()
  const match = value.match(/\/join\/([^/?#\s]+)/)
  return match ? match[1] : value.replace(/[/?#\s].*$/, '')
}

// Students join only through the teacher's invitation link (/join/:code); course numbers are not enough.
function JoinByLinkForm() {
  const navigate = useNavigate()
  const [link, setLink] = useState('')
  const code = inviteCodeFrom(link)

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (code) navigate(`/join/${encodeURIComponent(code)}`)
      }}
      className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 shadow-sm"
    >
      <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-white">
        <Search className="h-4 w-4 text-indigo-600" /> Записаться на курс
      </h2>
      <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
        Откройте ссылку-приглашение от преподавателя или вставьте её сюда.
      </p>
      <div className="mt-3 flex gap-2">
        <input
          value={link}
          onChange={(event) => setLink(event.target.value)}
          placeholder="Ссылка-приглашение"
          className={inputClass}
          aria-label="Ссылка-приглашение"
        />
        <button type="submit" disabled={!code} className="rounded-lg bg-indigo-600 px-3 text-sm font-semibold text-white transition hover:bg-indigo-700 disabled:opacity-60">
          Записаться
        </button>
      </div>
    </form>
  )
}
