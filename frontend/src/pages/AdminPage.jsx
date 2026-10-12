import { useCallback, useEffect, useState } from 'react'
import { BadgeCheck, BookOpen, CircleAlert, ClipboardList, FileStack, GraduationCap, HardDrive, Loader2, ScrollText, Save, ShieldCheck, Trash2, Users } from 'lucide-react'
import { Link } from 'react-router-dom'
import api, { getErrorMessage } from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { ROLES, ROLE_LABELS, ROLE_TEACHER } from '../constants/roles'
import Navbar from '../components/Navbar'
import Toast from '../components/Toast'
import ConfirmDialog from '../components/ConfirmDialog'
import { Alert } from '../components/FormControls'

const ROLE_BADGE_STYLES = {
  ROLE_STUDENT: 'bg-sky-50 dark:bg-sky-500/10 text-sky-700 dark:text-sky-300 ring-sky-200 dark:ring-sky-500/30',
  ROLE_TEACHER: 'bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-300 ring-amber-200 dark:ring-amber-500/30',
  ROLE_ADMIN: 'bg-violet-50 dark:bg-violet-500/10 text-violet-700 dark:text-violet-300 ring-violet-200 dark:ring-violet-500/30',
}

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} Б`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} КБ`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} МБ`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} ГБ`
}

export default function AdminPage() {
  const { user: currentUser } = useAuth()

  const [stats, setStats] = useState(null)
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedRoles, setSelectedRoles] = useState({}) // userId -> role picked in the dropdown
  const [savingId, setSavingId] = useState(null)
  const [toast, setToast] = useState(null)
  const [deleteTarget, setDeleteTarget] = useState(null) // user awaiting delete confirmation
  const [deleting, setDeleting] = useState(false)

  const closeToast = useCallback(() => setToast(null), [])
  // A fresh id restarts the auto-close timer and the enter animation even when a toast is already shown.
  const showToast = (type, message) => setToast((prev) => ({ id: (prev?.id ?? 0) + 1, type, message }))

  useEffect(() => {
    Promise.all([api.get('/admin/stats'), api.get('/admin/users')])
      .then(([statsResponse, usersResponse]) => {
        setStats(statsResponse.data)
        setUsers(usersResponse.data)
      })
      .catch((err) => setError(getErrorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

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
      showToast('success', `Роль пользователя ${data.email} успешно изменена`)
    } catch (err) {
      showToast('error', getErrorMessage(err))
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
      setUsers((prev) => prev.filter((item) => item.id !== target.id))
      setDeleteTarget(null)
      showToast('success', `Пользователь ${target.email} удалён`)
      // Documents and disk usage changed along with the user.
      api.get('/admin/stats').then(({ data }) => setStats(data)).catch(() => {})
    } catch (err) {
      setDeleteTarget(null)
      showToast('error', getErrorMessage(err))
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

  return (
    <div className="min-h-screen bg-slate-50/50 dark:bg-slate-950">
      <Navbar />
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-violet-600 to-indigo-600 text-white shadow-md shadow-violet-200">
            <ShieldCheck className="h-5 w-5" />
          </span>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Панель администратора</h1>
            <p className="text-sm text-slate-500 dark:text-slate-400">Пользователи, роли, курсы и системный журнал</p>
          </div>
        </div>

        {error && (
          <div className="mt-6">
            <Alert>{error}</Alert>
          </div>
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
                    {loading ? <span className="inline-block h-8 w-16 animate-pulse rounded bg-slate-100 dark:bg-slate-800" /> : (value ?? '—')}
                  </p>
                  {hint && <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">{hint}</p>}
                </div>
                <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br text-white ${accent}`}>
                  <Icon className="h-5 w-5" />
                </span>
              </div>
            </div>
          ))}
        </section>

        <section className="mt-8 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
          <div className="border-b border-slate-200 dark:border-slate-800 px-5 py-4">
            <h2 className="font-semibold text-slate-900 dark:text-white">Пользователи</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              После смены роли пользователю нужно войти заново — старый токен перестаёт действовать. Бывший студент
              отчисляется со всех курсов, курсы бывшего преподавателя остаются без преподавателя.
            </p>
          </div>

          {loading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-slate-500 dark:text-slate-400">
              <Loader2 className="h-4 w-4 animate-spin" /> Загрузка пользователей…
            </div>
          ) : users.length === 0 ? (
            <p className="py-16 text-center text-sm text-slate-500 dark:text-slate-400">Пользователей нет</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[860px] text-left text-sm">
                <thead className="border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400">
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
                              ROLE_BADGE_STYLES[item.role] ?? 'bg-slate-50 dark:bg-slate-800/50 text-slate-700 dark:text-slate-200 ring-slate-200 dark:ring-slate-700'
                            }`}
                            title={item.role}
                          >
                            {ROLE_LABELS[item.role] ?? item.role}
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
                              <option key={value} value={value}>{label} ({value})</option>
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
          )}
        </section>

        {!loading && <AdminCourses teachers={users.filter((item) => item.role === ROLE_TEACHER)} showToast={showToast} />}
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

      <Toast toast={toast} onClose={closeToast} />
    </div>
  )
}

const tableHead = 'border-b border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400'

/** All courses with their teacher; ADMIN can hand a course over to another teacher. */
function AdminCourses({ teachers, showToast }) {
  const [courses, setCourses] = useState(null)
  const [savingId, setSavingId] = useState(null)

  useEffect(() => {
    api.get('/courses', { params: { size: 100, sort: 'title' } })
      .then(({ data }) => setCourses(data.content))
      .catch((err) => {
        setCourses([])
        showToast('error', getErrorMessage(err))
      })
    // showToast is recreated on every render of the page; the list is loaded once.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const assign = async (course, teacherId) => {
    if (!teacherId) return
    setSavingId(course.id)
    try {
      const { data } = await api.patch(`/courses/${course.id}/teacher`, { teacherId: Number(teacherId) })
      setCourses((prev) => prev.map((item) => (item.id === data.id ? data : item)))
      showToast('success', `Курс «${data.title}» передан преподавателю ${data.teacherEmail}`)
    } catch (err) {
      showToast('error', getErrorMessage(err))
    } finally {
      setSavingId(null)
    }
  }

  return (
    <section className="mt-8 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
      <div className="border-b border-slate-200 dark:border-slate-800 px-5 py-4">
        <h2 className="font-semibold text-slate-900 dark:text-white">Курсы</h2>
        <p className="text-xs text-slate-500 dark:text-slate-400">Преподаватель управляет только своими курсами и их документами.</p>
      </div>
      {courses === null ? (
        <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Загрузка курсов…</div>
      ) : courses.length === 0 ? (
        <p className="py-10 text-center text-sm text-slate-500 dark:text-slate-400">Курсов пока нет</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className={tableHead}>
              <tr>
                <th className="px-4 py-3 font-medium">Курс</th>
                <th className="px-4 py-3 font-medium">Преподаватель</th>
                <th className="px-4 py-3 font-medium">Назначить</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {courses.map((course) => (
                <tr key={course.id} className="hover:bg-slate-50/60 dark:hover:bg-slate-800/50">
                  <td className="px-4 py-3">
                    <Link to={`/courses/${course.id}`} className="font-medium text-slate-900 dark:text-white hover:text-indigo-600 hover:underline">
                      {course.code ? `${course.code} · ` : ''}{course.title}
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-slate-700 dark:text-slate-200">
                    {course.teacherEmail ?? <span className="text-amber-700 dark:text-amber-300">не назначен</span>}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <select
                        value=""
                        onChange={(event) => assign(course, event.target.value)}
                        disabled={savingId === course.id || teachers.length === 0}
                        aria-label={`Преподаватель курса ${course.title}`}
                        className="w-full min-w-[200px] rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-sm text-slate-900 dark:text-white outline-none focus:border-indigo-500 disabled:opacity-60"
                      >
                        <option value="">{teachers.length ? 'Выбрать преподавателя…' : 'Нет преподавателей'}</option>
                        {teachers.filter((teacher) => teacher.id !== course.teacherId).map((teacher) => (
                          <option key={teacher.id} value={teacher.id}>{teacher.fullName ? `${teacher.fullName} (${teacher.email})` : teacher.email}</option>
                        ))}
                      </select>
                      {savingId === course.id && <Loader2 className="h-4 w-4 animate-spin text-slate-400" />}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
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
  ROLE_CHANGED: 'Смена роли',
  USER_DELETED: 'Удаление пользователя',
}

const logDateFormatter = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'medium' })

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
                    <td className="whitespace-nowrap px-4 py-2.5 tabular-nums text-slate-500 dark:text-slate-400">{logDateFormatter.format(new Date(entry.createdAt))}</td>
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
