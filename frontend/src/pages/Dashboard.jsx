import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, BadgeCheck, BookOpen, CircleAlert, FileUp, Files, FolderLock, GraduationCap, Loader2, LockKeyhole, Mail, Plus, ShieldCheck, User } from 'lucide-react'
import api from '../api/axios'
import { useAuth } from '../context/AuthContext'
import { ROLES, ROLE_ADMIN, ROLE_LABELS, ROLE_STUDENT } from '../constants/roles'
import Navbar from '../components/Navbar'
import AiChat from '../components/AiChat'
import MyTasks from '../components/MyTasks'
import UpcomingDeadlines from '../components/UpcomingDeadlines'

const CHAT_SUGGESTIONS = ['Какие дедлайны на этой неделе?', 'Какие лабораторные нужно сдать?', 'Как выставляется итоговая оценка?']

// What each role's dashboard says about access, after the core rule.
const ACCESS_TEXT = {
  ROLE_STUDENT: { files: 'Документы ваших курсов', access: 'Только ваши курсы', hint: 'Курсы, на которые вы записаны, и их документы' },
  ROLE_TEACHER: { files: 'Документы ваших курсов', access: 'Ваши курсы', hint: 'Вы управляете курсами, которые ведёте, и их документами' },
  ROLE_ADMIN: { files: 'Все документы системы', access: 'Вся система', hint: 'Пользователи, курсы, роли и системный журнал' },
}

export default function Dashboard() {
  const { user } = useAuth()
  const isStudent = user.role === ROLE_STUDENT
  const isAdmin = user.role === ROLE_ADMIN
  const access = ACCESS_TEXT[user.role] ?? ACCESS_TEXT.ROLE_STUDENT
  const [documentCount, setDocumentCount] = useState(null) // null while loading, undefined if the request failed

  useEffect(() => {
    let cancelled = false
    api.get('/documents', { params: { size: 1 } })
      .then(({ data }) => !cancelled && setDocumentCount(data.totalElements))
      .catch(() => !cancelled && setDocumentCount(undefined))
    return () => {
      cancelled = true
    }
  }, [])

  const RoleIcon = ROLES.find((role) => role.value === user.role)?.icon ?? User
  const initials = (user.fullName || user.email)
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  return (
    <div className="min-h-screen bg-slate-50/50 dark:bg-slate-950">
      <Navbar />

      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6 sm:py-10">
        <section className="relative overflow-hidden rounded-2xl border border-indigo-100 dark:border-indigo-500/30 bg-white dark:bg-slate-900 shadow-sm">
          <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-indigo-500 via-violet-500 to-cyan-400" />
          <div className="grid gap-6 px-6 py-7 sm:px-8 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
            <div>
              <span className="inline-flex items-center gap-2 rounded-full bg-indigo-50 dark:bg-indigo-500/10 px-3 py-1 text-xs font-semibold text-indigo-700 dark:text-indigo-300">
                <ShieldCheck className="h-3.5 w-3.5" />
                Защищённая рабочая среда
              </span>
              <h1 className="mt-4 text-2xl font-bold text-slate-900 dark:text-white sm:text-3xl">Добро пожаловать в UniDocs Assistant</h1>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-300 sm:text-base">
                {isStudent
                  ? 'Материалы ваших курсов, дедлайны и ИИ-ассистент, который отвечает только по документам курсов, на которые вы записаны.'
                  : isAdmin
                    ? 'Управление всей системой: пользователи, роли, курсы и системный журнал.'
                    : 'Ваши курсы, их документы, студенты и задания. ИИ находит дедлайны в силлабусах и выдаёт их студентам курса.'}
              </p>
            </div>
            <div className="flex items-center gap-3 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/80 dark:bg-slate-800/50 p-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-violet-600 text-sm font-bold text-white shadow-md shadow-indigo-200">
                {initials}
              </span>
              <div className="min-w-0">
                <p className="max-w-44 truncate text-sm font-semibold text-slate-900 dark:text-white">{user.fullName || user.email}</p>
                <p className="mt-0.5 flex items-center gap-1.5 text-xs text-emerald-700 dark:text-emerald-300">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                  Профиль активен
                </p>
              </div>
            </div>
          </div>
        </section>
        <section className="mt-6 grid gap-4 md:grid-cols-3">
          <StatusCard icon={Files} iconClass="bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400" label="Всего файлов" value={documentCount === null ? '…' : documentCount ?? '—'} description={access.files} />
          <StatusCard icon={LockKeyhole} iconClass="bg-emerald-50 dark:bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" label="Доступ" value={access.access} description={access.hint} />
          <StatusCard icon={RoleIcon} iconClass="bg-violet-50 dark:bg-violet-500/10 text-violet-600 dark:text-violet-400" label="Текущие права доступа" value={ROLE_LABELS[user.role] ?? user.role} description="Доступ определяется вашей ролью" />
        </section>

        <h2 className="mt-8 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">Быстрые действия</h2>
        <section className="mt-3 grid gap-4 sm:grid-cols-3">
          <QuickAction to="/courses" icon={GraduationCap} title={isAdmin ? 'Все курсы' : 'Мои курсы'} description="Документы, задания и чат с ИИ" accent="violet" />
          <QuickAction to="/documents" icon={FolderLock} title="Хранилище" description={access.files} />
          {isStudent ? (
            <QuickAction to="/courses" icon={Plus} title="Записаться на курс" description="По ссылке или номеру от преподавателя" accent="indigo" />
          ) : isAdmin ? (
            <QuickAction to="/admin" icon={ShieldCheck} title="Админ-панель" description="Пользователи, роли, курсы, журнал" accent="indigo" />
          ) : (
            <QuickAction to="/documents" icon={FileUp} title="Загрузить документ" description="В один из ваших курсов" accent="indigo" />
          )}
        </section>

        {isStudent ? (
          <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
            <div className="space-y-6">
              <UpcomingDeadlines />
              <MyTasks />
            </div>
            <div className="space-y-6">
              <MyCourses title="Мои курсы" empty="Вы пока не записаны ни на один курс." />
              <AiChat
                endpoint="/ai/ask"
                title="Чат с ИИ по моим курсам"
                subtitle="Ответы только по документам курсов, на которые вы записаны"
                placeholder="Например: когда сдавать Practice 04?"
                suggestions={CHAT_SUGGESTIONS}
                showDocuments
              />
            </div>
          </div>
        ) : !isAdmin && (
          <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
            <MyCourses title="Мои курсы" empty="Вы пока не ведёте курсов. Создайте курс на странице «Курсы»." />
            <AiChat
              endpoint="/ai/ask"
              title="Чат с ИИ по моим курсам"
              subtitle="Поиск по документам курсов, которые вы ведёте"
              placeholder="Например: какие темы в силлабусе?"
              suggestions={CHAT_SUGGESTIONS}
              showDocuments
            />
          </div>
        )}

        <section id="profile" className="mt-8 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-sm">
          <div className="h-24 bg-gradient-to-r from-indigo-600 via-violet-600 to-cyan-500" />
          <div className="px-6 pb-6">
            <div className="-mt-10 flex h-20 w-20 items-center justify-center rounded-2xl border-4 border-white dark:border-slate-900 bg-indigo-100 dark:bg-indigo-500/20 text-2xl font-bold text-indigo-700 dark:text-indigo-300 shadow">
              {initials}
            </div>
            <h2 className="mt-3 text-xl font-semibold text-slate-900 dark:text-white">{user.fullName}</h2>

            <dl className="mt-5 grid gap-4 sm:grid-cols-3">
              <InfoItem icon={Mail} label="Email" value={user.email} />
              <InfoItem icon={RoleIcon} label="Роль" value={ROLE_LABELS[user.role] ?? user.role} />
              {user.emailVerified !== undefined && (
                <InfoItem
                  icon={user.emailVerified ? BadgeCheck : CircleAlert}
                  label="Email подтверждён"
                  value={user.emailVerified ? 'Да' : 'Нет'}
                />
              )}
            </dl>
          </div>
        </section>
      </main>
    </div>
  )
}

function MyCourses({ title, empty }) {
  const [courses, setCourses] = useState(null)

  useEffect(() => {
    let cancelled = false
    api.get('/courses', { params: { size: 6 } })
      .then(({ data }) => !cancelled && setCourses(data))
      .catch(() => !cancelled && setCourses({ content: [], totalElements: 0 }))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-sm">
      <div className="flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900 dark:text-white">
          <BookOpen className="h-5 w-5 text-violet-600 dark:text-violet-400" /> {title}
        </h2>
        <Link to="/courses" className="text-xs font-medium text-indigo-600 dark:text-indigo-400 hover:underline">Все курсы</Link>
      </div>
      {courses === null ? (
        <div className="flex items-center justify-center gap-2 py-8 text-sm text-slate-500"><Loader2 className="h-4 w-4 animate-spin" /> Загрузка…</div>
      ) : courses.content.length === 0 ? (
        <p className="mt-4 rounded-xl border border-dashed border-slate-300 dark:border-slate-700 px-4 py-6 text-center text-sm text-slate-500 dark:text-slate-400">{empty}</p>
      ) : (
        <ul className="mt-4 space-y-2">
          {courses.content.map((course) => (
            <li key={course.id}>
              <Link to={`/courses/${course.id}`} className="group flex items-center gap-3 rounded-xl border border-slate-200 dark:border-slate-800 px-3 py-2.5 transition hover:border-indigo-200 dark:hover:border-indigo-500/30 hover:bg-indigo-50/40 dark:hover:bg-indigo-500/10">
                {course.code && <span className="rounded-md bg-indigo-50 dark:bg-indigo-500/10 px-1.5 py-0.5 text-xs font-bold text-indigo-700 dark:text-indigo-300">{course.code}</span>}
                <span className="min-w-0 flex-1 truncate text-sm font-medium text-slate-900 dark:text-white">{course.title}</span>
                <ArrowRight className="h-4 w-4 shrink-0 text-slate-400 transition-transform group-hover:translate-x-1 group-hover:text-indigo-600" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function InfoItem({ icon: Icon, label, value }) {
  return (
    <div className="flex items-start gap-3 rounded-xl bg-slate-50 dark:bg-slate-800/50 p-4">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white dark:bg-slate-900 text-indigo-600 dark:text-indigo-400 shadow-sm">
        <Icon className="h-4.5 w-4.5" />
      </span>
      <div className="min-w-0">
        <dt className="text-xs font-medium uppercase tracking-wide text-slate-400 dark:text-slate-500">{label}</dt>
        <dd className="mt-0.5 truncate text-sm font-medium text-slate-900 dark:text-white">{value}</dd>
      </div>
    </div>
  )
}

function StatusCard({ icon: Icon, iconClass, label, value, description }) {
  return (
    <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:border-indigo-200 dark:hover:border-indigo-500/30 hover:shadow-md">
      <span className={`flex h-10 w-10 items-center justify-center rounded-xl ${iconClass}`}>
        <Icon className="h-5 w-5" />
      </span>
      <p className="mt-4 text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">{label}</p>
      <p className="mt-1 text-base font-bold text-slate-900 dark:text-white">{value}</p>
      <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{description}</p>
    </div>
  )
}

function QuickAction({ to, icon: Icon, title, description, accent = 'slate' }) {
  const accentClasses = accent === 'indigo'
    ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 group-hover:bg-indigo-600 group-hover:text-white'
    : accent === 'violet'
      ? 'bg-violet-50 dark:bg-violet-500/10 text-violet-600 dark:text-violet-400 group-hover:bg-violet-600 group-hover:text-white'
      : 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 group-hover:bg-slate-800 group-hover:text-white'

  return (
    <Link to={to} className="group rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-4 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:border-indigo-200 dark:hover:border-indigo-500/30 hover:shadow-md">
      <span className={`flex h-9 w-9 items-center justify-center rounded-lg transition-all duration-200 ${accentClasses}`}>
        <Icon className="h-4.5 w-4.5" />
      </span>
      <p className="mt-4 text-sm font-semibold text-slate-900 dark:text-white">{title}</p>
      <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">{description}</p>
      <ArrowRight className="mt-3 h-4 w-4 text-slate-400 dark:text-slate-500 transition-transform duration-200 group-hover:translate-x-1 group-hover:text-indigo-600 dark:group-hover:text-indigo-400" />
    </Link>
  )
}
