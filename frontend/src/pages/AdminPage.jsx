import { useCallback, useEffect, useState } from 'react'
import { BadgeCheck, BookOpen, CircleAlert, ClipboardList, FileStack, GraduationCap, HardDrive, Loader2, ScrollText, Save, Search, ShieldCheck, Trash2, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import api, { getErrorMessage } from '../api/axios'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import { DEFAULT_ROLE_BADGE_STYLE, ROLES, ROLE_BADGE_STYLES, ROLE_TEACHER, roleLabel } from '../constants/roles'
import Navbar from '../components/Navbar'
import ConfirmDialog from '../components/ConfirmDialog'
import { Alert } from '../components/FormControls'
import AdminTeacherRequests from '../components/AdminTeacherRequests'
import ErrorState from '../components/ErrorState'
import SearchSelect from '../components/SearchSelect'
import { formatDateTime } from '../utils/dates'


function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} Б`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} КБ`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} МБ`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} ГБ`
}

const USERS_PAGE_SIZE = 20
const COURSES_PAGE_SIZE = 20

export default function AdminPage() {
  const { user: currentUser } = useAuth()
  const { notifyError, showSuccess } = useToast()

  const [stats, setStats] = useState(null)
  const [statsError, setStatsError] = useState('')
  const [selectedRoles, setSelectedRoles] = useState({}) // userId -> role picked in the dropdown
  const [savingId, setSavingId] = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null) // user awaiting delete confirmation
  const [deleting, setDeleting] = useState(false)
  const [requestsVersion, setRequestsVersion] = useState(0) // Bumped after role changes, see AdminTeacherRequests.

  // Users: server pages, searched by email or name (2+ characters, 300 ms after the last keystroke).
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('') // '' or the debounced search term
  const [usersPage, setUsersPage] = useState(null) // { ...PageResponse, forQuery }
  const [usersError, setUsersError] = useState('')
  const [usersAttempt, setUsersAttempt] = useState(0)
  const [loadingMoreUsers, setLoadingMoreUsers] = useState(false)


  const loadStats = useCallback(() => api.get('/admin/stats')
    .then(({ data }) => {
      setStats(data)
      setStatsError('')
    })
    .catch((err) => setStatsError(getErrorMessage(err))), [])

  useEffect(() => {
    loadStats()
  }, [loadStats])

  useEffect(() => {
    const timer = setTimeout(() => setQuery(search.trim().length >= 2 ? search.trim() : ''), 300)
    return () => clearTimeout(timer)
  }, [search])

  const fetchUsers = useCallback((number, term) => api.get('/admin/users', {
    params: { page: number, size: USERS_PAGE_SIZE, ...(term ? { q: term } : {}) },
  }).then(({ data }) => data), [])

  useEffect(() => {
    let cancelled = false
    fetchUsers(0, query)
      .then((data) => {
        if (cancelled) return
        setUsersPage({ ...data, forQuery: query })
        setUsersError('')
      })
      .catch((err) => !cancelled && setUsersError(getErrorMessage(err)))
    return () => {
      cancelled = true
    }
  }, [fetchUsers, query, usersAttempt])

  const users = usersPage?.content ?? []
  // Loading: nothing yet, or the shown page belongs to an older search term.
  const usersLoading = !usersError && (!usersPage || usersPage.forQuery !== query)
  const setUsers = (update) => setUsersPage((current) => current && ({ ...current, content: update(current.content) }))

  const loadMoreUsers = async () => {
    setLoadingMoreUsers(true)
    try {
      const next = await fetchUsers(usersPage.page + 1, query)
      setUsersPage((current) => ({ ...next, forQuery: query, content: [...current.content, ...next.content] }))
    } catch (err) {
      notifyError(err)
    } finally {
      setLoadingMoreUsers(false)
    }
  }

  const handleSave = async (target) => {
    const role = selectedRoles[target.id]
    if (!role || role === target.role) return
    setSavingId(target.id)
    try {
      const { data } = await api.patch(`/admin/users/${target.id}/role`, { role })
      setUsers((prev) => prev.map((item) => (item.id === data.id ? data : item)))
      setSelectedRoles((prev) => {
        const next = { ...prev }
        delete next[target.id]
        return next
      })
      showSuccess(`Роль пользователя ${data.email} успешно изменена`)
      // A promotion settles a pending teacher request; the requests list must not offer it any more.
      setRequestsVersion((value) => value + 1)
    } catch (err) {
      notifyError(err)
    } finally {
      setSavingId(null)
    }
  }

  const cancelDelete = useCallback(() => setDeleteTarget(null), [])

  const handleDelete = async () => {
    const target = deleteTarget
    setDeleting(true)
    try {
      await api.delete(`/admin/users/${target.id}`)
      setUsersPage((current) => current && ({
        ...current,
        totalElements: Math.max(0, current.totalElements - 1),
        content: current.content.filter((item) => item.id !== target.id),
      }))
      setDeleteTarget(null)
      showSuccess(`Пользователь ${target.email} удалён`)
      // Documents and disk usage changed along with the user; a pending request of theirs is gone too.
      loadStats()
      setRequestsVersion((value) => value + 1)
    } catch (err) {
      setDeleteTarget(null)
      notifyError(err)
    } finally {
      setDeleting(false)
    }
  }

  const cards = [
    { icon: Users, label: 'Всего пользователей', value: stats?.totalUsers, accent: 'from-indigo-500 to-violet-500' },
    { icon: FileStack, label: 'Документов в хранилище', value: stats?.totalDocuments, accent: 'from-cyan-500 to-sky-500' },
    {
      icon: HardDrive,
      label: 'Использование диска',
      value: stats ? formatBytes(stats.totalStorageBytes) : undefined,
      hint: 'Суммарный размер файлов',
      accent: 'from-emerald-500 to-teal-500',
    },
    { icon: BookOpen, label: 'Курсов', value: stats?.totalCourses, accent: 'from-violet-500 to-fuchsia-500' },
    { icon: GraduationCap, label: 'Записей на курсы', value: stats?.totalEnrollments, accent: 'from-amber-500 to-orange-500' },
    { icon: ClipboardList, label: 'Заданий', value: stats?.totalAssignments, accent: 'from-rose-500 to-pink-500' },
  ]
  const statsLoading = stats === null && !statsError

  return (
    <div className="min-h-screen bg-slate-50/50 dark:bg-slate-950">
      <Navbar />
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-violet-600 to-indigo-600 text-white shadow-md shadow-violet-200">
            <ShieldCheck className="h-5 w-5" />
          </span>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Панель деканата</h1>
            <p className="text-sm text-slate-500 dark:text-slate-400">Пользователи, роли, курсы и системный журнал</p>
          </div>
        </div>

        {statsError && (
          <ErrorState compact message={`Не удалось загрузить статистику: ${statsError}`} onRetry={loadStats} className="mt-6" />
        )}

        <section className="mt-6 grid gap-4 sm:grid-cols-3">
          {cards.map(({ icon: Icon, label, value, hint, accent }) => (
            <div
              key={label}
              className="relative overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md"
            >
              <div className={`absolute inset-x-0 top-0 h-1 bg-gradient-to-r ${accent}`} />
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">{label}</p>
                  <p className="mt-2 text-3xl font-bold tabular-nums text-slate-900 dark:text-white">
                    {statsLoading ? <span className="inline-block h-8 w-16 animate-pulse rounded bg-slate-100 dark:bg-slate-800" /> : (value ?? '—')}
                  </p>
                  {hint && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{hint}</p>}
                </div>
                <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br text-white ${accent}`}>
                  <Icon className="h-5 w-5" />
                </span>
              </div>
            </div>
          ))}
        </section>

        <AdminTeacherRequests
          refreshKey={requestsVersion}
          onDecided={(updated) => setUsers((current) => current.map((item) => (item.id === updated.id ? updated : item)))}
        />

        <section className="mt-8 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
          <div className="flex flex-col gap-3 border-b border-slate-200 dark:border-slate-800 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0">
              <h2 className="flex items-center gap-2 font-semibold text-slate-900 dark:text-white">
                Пользователи
                {usersPage && <span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:text-slate-300">{usersPage.totalElements}</span>}
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                После смены роли пользователю нужно войти заново — старый токен перестаёт действовать. Бывший студент
                отчисляется со всех курсов, курсы бывшего преподавателя остаются без преподавателя.
              </p>
            </div>
            <label className="relative block w-full shrink-0 sm:w-64">
              <span className="sr-only">Поиск по email или имени</span>
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-500 dark:text-slate-400" />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Email или имя (от 2 символов)"
                className="w-full rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 py-1.5 pl-8 pr-2.5 text-sm text-slate-900 dark:text-white outline-none transition placeholder:text-slate-500 dark:placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100 dark:focus:ring-indigo-500/30"
              />
            </label>
          </div>

          {usersError ? (
            <ErrorState
              title="Не удалось загрузить пользователей"
              message={usersError}
              onRetry={() => {
                setUsersError('')
                setUsersAttempt((value) => value + 1)
              }}
            />
          ) : usersLoading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500 dark:text-slate-400">
              <Loader2 className="h-4 w-4 animate-spin" /> {query ? 'Поиск…' : 'Загрузка пользователей…'}
            </div>
          ) : users.length === 0 ? (
            <p className="py-16 text-center text-sm text-slate-500 dark:text-slate-400">
              {query ? `Никого не найдено по запросу «${query}»` : 'Пользователей нет'}
            </p>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[860px] text-left text-sm">
                  <thead className={tableHead}>
                    <tr>
                      <th className="px-4 py-3 font-medium">ID</th>
                      <th className="px-4 py-3 font-medium">Имя</th>
                      <th className="px-4 py-3 font-medium">Email</th>
                      <th className="px-4 py-3 font-medium">Статус Email</th>
                      <th className="px-4 py-3 font-medium">Текущая роль</th>
                      <th className="px-4 py-3 font-medium">Новая роль</th>
                      <th className="px-4 py-3 font-medium" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                    {users.map((item) => {
                      const isSelf = item.email === currentUser?.email
                      const selected = selectedRoles[item.id] ?? item.role
                      const changed = selected !== item.role
                      const saving = savingId === item.id
                      return (
                        <tr key={item.id} className="hover:bg-slate-50/60 dark:hover:bg-slate-800/50">
                          <td className="px-4 py-3 tabular-nums text-slate-500 dark:text-slate-400">{item.id}</td>
                          <td className="max-w-[200px] px-4 py-3">
                            <span className="block truncate font-medium text-slate-900 dark:text-white" title={item.fullName}>
                              {item.fullName || '—'}
                            </span>
                            {isSelf && <span className="text-xs text-indigo-600 dark:text-indigo-400">Это вы</span>}
                          </td>
                          <td className="max-w-[220px] px-4 py-3">
                            <span className="block truncate text-slate-700 dark:text-slate-200" title={item.email}>{item.email}</span>
                          </td>
                          <td className="whitespace-nowrap px-4 py-3">
                            {item.emailVerified ? (
                              <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                                <BadgeCheck className="h-4 w-4" /> Подтверждён
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 text-xs font-medium text-amber-700 dark:text-amber-300">
                                <CircleAlert className="h-4 w-4" /> Не подтверждён
                              </span>
                            )}
                          </td>
                          <td className="whitespace-nowrap px-4 py-3">
                            <span
                              className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${
                                ROLE_BADGE_STYLES[item.role] ?? DEFAULT_ROLE_BADGE_STYLE
                              }`}
                            >
                              {roleLabel(item.role)}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            <select
                              value={selected}
                              onChange={(event) => setSelectedRoles((prev) => ({ ...prev, [item.id]: event.target.value }))}
                              disabled={isSelf || saving}
                              title={isSelf ? 'Нельзя изменить собственную роль' : undefined}
                              aria-label={`Новая роль для ${item.email}`}
                              className="w-full min-w-[150px] rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-sm text-slate-900 dark:text-white outline-none transition focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100 dark:focus:ring-indigo-500/30 disabled:cursor-not-allowed disabled:bg-slate-50 dark:disabled:bg-slate-800/50 disabled:text-slate-400 dark:disabled:text-slate-500"
                            >
                              {ROLES.map(({ value, label }) => (
                                <option key={value} value={value}>{label}</option>
                              ))}
                            </select>
                          </td>
                          <td className="whitespace-nowrap px-4 py-3">
                            <div className="flex justify-end gap-2">
                              <button
                                type="button"
                                onClick={() => handleSave(item)}
                                disabled={!changed || saving}
                                className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:bg-slate-200 dark:disabled:bg-slate-700 disabled:text-slate-400 dark:disabled:text-slate-500 disabled:shadow-none"
                              >
                                {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
                                Сохранить
                              </button>
                              <button
                                type="button"
                                onClick={() => setDeleteTarget(item)}
                                disabled={isSelf || saving}
                                title={isSelf ? 'Нельзя удалить собственный аккаунт' : 'Удалить пользователя'}
                                className="inline-flex items-center gap-1.5 rounded-lg border border-red-200 dark:border-red-500/30 px-3 py-1.5 text-xs font-semibold text-red-600 dark:text-red-400 transition hover:bg-red-50 dark:hover:bg-red-500/10 disabled:cursor-not-allowed disabled:border-slate-200 dark:disabled:border-slate-800 disabled:text-slate-400 dark:disabled:text-slate-500 disabled:hover:bg-transparent"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                                Удалить
                              </button>
                            </div>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
              {!usersPage.last && (
                <ShowMoreButton loading={loadingMoreUsers} onClick={loadMoreUsers} />
              )}
            </>
          )}
        </section>

        <AdminCourses />
        <AdminLogs />
      </main>

      {deleteTarget && (
        <ConfirmDialog
          title="Удалить пользователя?"
          confirmLabel="Удалить навсегда"
          loading={deleting}
          onConfirm={handleDelete}
          onCancel={cancelDelete}
        >
          Вы уверены, что хотите удалить пользователя <b className="text-slate-900 dark:text-white">{deleteTarget.email}</b>?
          Все его документы будут безвозвратно удалены.
        </ConfirmDialog>
      )}

    </div>
  )
}

function ShowMoreButton({ loading, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={loading}
      className="flex w-full items-center justify-center gap-2 border-t border-slate-100 dark:border-slate-800 py-2.5 text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60"
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" />} Показать ещё
    </button>
  )
}

const tableHead = 'border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400'

/** All courses with their teacher, a page at a time; ADMIN can hand a course over to another teacher. */
function AdminCourses() {
  const { notifyError, showSuccess } = useToast()
  const [page, setPage] = useState(null)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [loadingMore, setLoadingMore] = useState(false)
  const [savingId, setSavingId] = useState(null)

  const fetchPage = useCallback((number) => api.get('/courses', { params: { page: number, size: COURSES_PAGE_SIZE, sort: 'title' } })
    .then(({ data }) => data), [])

  useEffect(() => {
    let cancelled = false
    fetchPage(0)
      .then((data) => {
        if (cancelled) return
        setPage(data)
        setError('')
      })
      .catch((err) => !cancelled && setError(getErrorMessage(err)))
    return () => {
      cancelled = true
    }
  }, [fetchPage, attempt])

  const loadMore = async () => {
    setLoadingMore(true)
    try {
      const next = await fetchPage(page.page + 1)
      setPage((current) => ({ ...next, content: [...current.content, ...next.content] }))
    } catch (err) {
      notifyError(err)
    } finally {
      setLoadingMore(false)
    }
  }

  const assign = async (course, teacherId) => {
    if (!teacherId) return
    setSavingId(course.id)
    try {
      const { data } = await api.patch(`/courses/${course.id}/teacher`, { teacherId: Number(teacherId) })
      setPage((current) => ({ ...current, content: current.content.map((item) => (item.id === data.id ? data : item)) }))
      showSuccess(`Курс «${data.title}» передан преподавателю ${data.teacherEmail}`)
    } catch (err) {
      notifyError(err)
    } finally {
      setSavingId(null)
    }
  }

  return (
    <section className="mt-8 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
      <div className="border-b border-slate-200 dark:border-slate-800 px-5 py-4">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900 dark:text-white">
          Курсы
          {page && <span className="rounded-full bg-slate-100 dark:bg-slate-800 px-2 py-0.5 text-xs font-semibold text-slate-600 dark:text-slate-300">{page.totalElements}</span>}
        </h2>
        <p className="text-xs text-slate-500 dark:text-slate-400">Преподаватель управляет только своими курсами и их документами.</p>
      </div>
      {error ? (
        <ErrorState
          title="Не удалось загрузить курсы"
          message={error}
          onRetry={() => {
            setError('')
            setPage(null)
            setAttempt((value) => value + 1)
          }}
        />
      ) : page === null ? (
        <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Загрузка курсов…</div>
      ) : page.content.length === 0 ? (
        <p className="py-10 text-center text-sm text-slate-500 dark:text-slate-400">Курсов пока нет</p>
      ) : (
        <>
          {/* No overflow-x wrapper: the teacher picker's dropdown must not be clipped. */}
          <table className="w-full text-left text-sm">
            <thead className={tableHead}>
              <tr>
                <th className="px-4 py-3 font-medium">Курс</th>
                <th className="hidden px-4 py-3 font-medium sm:table-cell">Преподаватель</th>
                <th className="px-4 py-3 font-medium">Назначить</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {page.content.map((course) => (
                <tr key={course.id} className="hover:bg-slate-50/60 dark:hover:bg-slate-800/50">
                  <td className="px-4 py-3">
                    <Link to={`/courses/${course.id}`} className="font-medium text-slate-900 dark:text-white hover:text-indigo-600 hover:underline">
                      {course.code ? `${course.code} · ` : ''}{course.title}
                    </Link>
                    <span className="mt-0.5 block text-xs text-slate-600 dark:text-slate-300 sm:hidden">
                      {course.teacherEmail ?? 'преподаватель не назначен'}
                    </span>
                  </td>
                  <td className="hidden px-4 py-3 text-slate-700 dark:text-slate-200 sm:table-cell">
                    {course.teacherEmail ?? <span className="text-amber-700 dark:text-amber-300">не назначен</span>}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <TeacherPicker course={course} disabled={savingId === course.id} onPick={(teacherId) => assign(course, teacherId)} />
                      {savingId === course.id && <Loader2 className="h-4 w-4 shrink-0 animate-spin text-slate-400" />}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!page.last && <ShowMoreButton loading={loadingMore} onClick={loadMore} />}
        </>
      )}
    </section>
  )
}

/** Searches teachers on the server (any number of them); the course's current teacher is not offered. */
function TeacherPicker({ course, disabled, onPick }) {
  const fetchTeachers = useCallback((term) => api.get('/admin/users', {
    params: { role: ROLE_TEACHER, size: 20, ...(term ? { q: term } : {}) },
  }).then(({ data }) => data.content
    .filter((teacher) => teacher.id !== course.teacherId)
    .map((teacher) => ({ value: String(teacher.id), label: teacher.fullName ? `${teacher.fullName} (${teacher.email})` : teacher.email }))),
  [course.teacherId])

  return (
    <SearchSelect
      value={null}
      onChange={(option) => onPick(option.value)}
      fetchOptions={fetchTeachers}
      placeholder="Выбрать преподавателя…"
      searchPlaceholder="Имя или email преподавателя"
      emptyText="Преподаватели не найдены"
      ariaLabel={`Преподаватель курса ${course.title}`}
      disabled={disabled}
      className="w-full min-w-[12rem]"
    />
  )
}

const ACTION_LABELS = {
  ACCESS_DENIED: 'Отказ в доступе',
  DOCUMENT_UPLOADED: 'Загрузка документа',
  DOCUMENT_DELETED: 'Удаление документа',
  COURSE_CREATED: 'Создание курса',
  COURSE_TEACHER_CHANGED: 'Смена преподавателя',
  STUDENT_ENROLLED: 'Запись на курс',
  STUDENT_UNENROLLED: 'Отчисление с курса',
  ASSIGNMENT_CREATED: 'Создание задания',
  ASSIGNMENT_DELETED: 'Удаление задания',
  ASSIGNMENT_CLOSED: 'Закрытие задания',
  ASSIGNMENT_PUBLISHED: 'Публикация заданий',
  ASSIGNMENT_REOPENED: 'Повторное открытие задания',
  ASSIGNMENT_DEADLINE_CHANGED: 'Перенос дедлайна',
  PII_ORIGINAL_VIEWED: 'Просмотр оригинала с личными данными',
  PII_ORIGINAL_ALLOWED: 'Студентам разрешён оригинал',
  PII_ORIGINAL_DENIED: 'Студентам закрыт оригинал',
  DOCUMENT_CATEGORY_SET: 'Тип документа указан вручную',
  OCR_RETRY_REQUESTED: 'Повторное распознавание скана',
  ROLE_CHANGED: 'Смена роли',
  USER_DELETED: 'Удаление пользователя',
  DOCUMENT_CLASSIFIED: 'Классификация документа',
  BATCH_IMPORTED: 'Пакетный импорт',
  TASK_SUBMITTED: 'Сдача решения',
  COURSE_INVITE_REGENERATED: 'Новая ссылка на курс',
  COURSE_JOIN_FAILED: 'Неудачная запись по ссылке',
  TEACHER_REQUEST_APPROVED: 'Заявка преподавателя одобрена',
  TEACHER_REQUEST_REJECTED: 'Заявка преподавателя отклонена',
  LOGIN_LOCKED: 'Блокировка входа',
}

/** System log: access denials and changes to users, courses, enrollments, documents and assignments. */
function AdminLogs() {
  const [page, setPage] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  // Loading is switched on by the buttons (and initially), not here: no synchronous setState in the effect.
  const fetchPage = useCallback((number) => api.get('/admin/logs', { params: { page: number, size: 50 } })
    .then(({ data }) => setPage((current) => (number === 0 ? data : { ...data, content: [...current.content, ...data.content] })))
    .catch((err) => setError(getErrorMessage(err)))
    .finally(() => setLoading(false)), [])

  useEffect(() => {
    fetchPage(0)
  }, [fetchPage])

  const load = (number) => {
    setLoading(true)
    fetchPage(number)
  }

  return (
    <section className="mt-8 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
      <div className="flex items-center justify-between gap-3 border-b border-slate-200 dark:border-slate-800 px-5 py-4">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900 dark:text-white">
          <ScrollText className="h-4 w-4 text-indigo-600" /> Системный журнал
        </h2>
        <button type="button" onClick={() => load(0)} disabled={loading} className="text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:underline disabled:opacity-60">Обновить</button>
      </div>
      {error ? (
        <div className="p-4"><Alert>{error}</Alert></div>
      ) : !page ? (
        <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Загрузка журнала…</div>
      ) : page.content.length === 0 ? (
        <p className="py-10 text-center text-sm text-slate-500 dark:text-slate-400">Событий пока нет</p>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className={tableHead}>
                <tr>
                  <th className="px-4 py-3 font-medium">Время</th>
                  <th className="px-4 py-3 font-medium">Пользователь</th>
                  <th className="px-4 py-3 font-medium">Событие</th>
                  <th className="px-4 py-3 font-medium">Подробности</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                {page.content.map((entry) => (
                  <tr key={entry.id}>
                    <td className="whitespace-nowrap px-4 py-2.5 tabular-nums text-slate-500 dark:text-slate-400">{formatDateTime(entry.createdAt)}</td>
                    <td className="max-w-[200px] truncate px-4 py-2.5 text-slate-700 dark:text-slate-200" title={entry.actorEmail}>{entry.actorEmail ?? '—'}</td>
                    <td className="whitespace-nowrap px-4 py-2.5">
                      <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${entry.action === 'ACCESS_DENIED'
                        ? 'bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-300'
                        : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'}`}
                      >
                        {ACTION_LABELS[entry.action] ?? entry.action}
                      </span>
                    </td>
                    <td className="px-4 py-2.5 text-slate-600 dark:text-slate-300">{entry.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!page.last && (
            <button type="button" onClick={() => load(page.page + 1)} disabled={loading} className="flex w-full items-center justify-center gap-2 border-t border-slate-100 dark:border-slate-800 py-2.5 text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:bg-slate-50 dark:hover:bg-slate-800 disabled:opacity-60">
              {loading && <Loader2 className="h-4 w-4 animate-spin" />} Показать ещё
            </button>
          )}
        </>
      )}
    </section>
  )
}
