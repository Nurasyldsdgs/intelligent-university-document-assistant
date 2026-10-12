import { Link, NavLink, useNavigate } from 'react-router-dom'
import { FileText, FolderLock, GraduationCap, LayoutDashboard, LogOut, ShieldCheck, User } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { ROLES, ROLE_ADMIN } from '../constants/roles'
import ThemeToggle from './ThemeToggle'

const ROLE_BADGE_STYLES = {
  ROLE_STUDENT: 'bg-sky-50 dark:bg-sky-500/10 text-sky-700 dark:text-sky-300 ring-sky-200 dark:ring-sky-500/30',
  ROLE_TEACHER: 'bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-300 ring-amber-200 dark:ring-amber-500/30',
  ROLE_ADMIN: 'bg-violet-50 dark:bg-violet-500/10 text-violet-700 dark:text-violet-300 ring-violet-200 dark:ring-violet-500/30',
}

export default function Navbar() {
  const { user, role, logout } = useAuth()
  const navigate = useNavigate()

  const roleInfo = ROLES.find((item) => item.value === role)
  const RoleIcon = roleInfo?.icon ?? User
  const initials = (user?.fullName || user?.email || 'U')
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const navLinkClass = ({ isActive }) => (
    `flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-200 ${
      isActive
        ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 shadow-sm ring-1 ring-inset ring-indigo-100 dark:ring-indigo-500/30'
        : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100/80 dark:hover:bg-slate-800/80 hover:text-slate-900 dark:hover:text-white'
    }`
  )

  return (
    <header className="sticky top-0 z-50 border-b border-slate-200/80 dark:border-slate-800 bg-white/80 dark:bg-slate-900/80 backdrop-blur-md">
      <div className="mx-auto flex min-h-16 max-w-6xl items-center justify-between gap-3 px-4 py-2 sm:px-6">
        <Link to="/dashboard" className="flex shrink-0 items-center gap-3" aria-label="UniDocs Assistant">
          <div className="relative flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-violet-600 text-white shadow-lg shadow-indigo-200/70">
            <FileText className="h-5 w-5" />
            <span className="absolute -right-1 -top-1 h-3 w-3 rounded-full border-2 border-white dark:border-slate-900 bg-cyan-400" />
          </div>
          <span className="hidden leading-tight sm:block">
            <span className="block text-sm font-bold text-slate-900 dark:text-white">UniDocs</span>
            <span className="block text-xs font-medium text-slate-500 dark:text-slate-400">Assistant</span>
          </span>
        </Link>

        <div className="flex min-w-0 items-center gap-3">
          <NavLink to="/dashboard" className={navLinkClass}>
            <LayoutDashboard className="h-4 w-4" />
            <span className="hidden lg:inline">Панель</span>
          </NavLink>
          <NavLink
            to="/documents"
            className={navLinkClass}
            title="Хранилище документов"
          >
            <FolderLock className="h-4 w-4" />
            <span className="hidden lg:inline">Хранилище документов</span>
          </NavLink>
          <NavLink to="/courses" className={navLinkClass} title="Курсы">
            <GraduationCap className="h-4 w-4" />
            <span className="hidden lg:inline">Курсы</span>
          </NavLink>
          {user?.role === ROLE_ADMIN && (
            <NavLink to="/admin" className={navLinkClass} title="Админ-панель">
              <ShieldCheck className="h-4 w-4" />
              <span className="hidden lg:inline">Админ-панель</span>
            </NavLink>
          )}
          {user && (
            <div className="flex min-w-0 items-center gap-2 rounded-xl border border-slate-200/80 dark:border-slate-800 bg-white/70 dark:bg-slate-900/70 py-1.5 pl-1.5 pr-2 sm:pr-3">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-100 dark:from-indigo-500/20 to-violet-100 dark:to-violet-500/20 text-xs font-bold text-indigo-700 dark:text-indigo-300 ring-2 ring-white dark:ring-slate-900">
                {initials}
              </span>
              <span className="hidden min-w-0 truncate text-sm font-medium text-slate-700 dark:text-slate-200 md:block">
                {user.fullName || user.email}
              </span>
              <span
                className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold ring-1 ring-inset ${
                  ROLE_BADGE_STYLES[role] ?? 'bg-slate-50 dark:bg-slate-800/50 text-slate-700 dark:text-slate-200 ring-slate-200 dark:ring-slate-700'
                }`}
                title={role ?? undefined}
              >
                <RoleIcon className="h-3 w-3" />
                {roleInfo?.label ?? role}
              </span>
            </div>
          )}
          <ThemeToggle />
          <button
            type="button"
            onClick={handleLogout}
            className="flex h-9 min-w-9 shrink-0 items-center justify-center gap-2 rounded-lg px-2 text-sm font-medium text-slate-500 dark:text-slate-400 transition-all duration-200 hover:bg-red-50 dark:hover:bg-red-500/10 hover:text-red-600 dark:hover:text-red-400 focus:outline-none focus:ring-4 focus:ring-red-100 dark:focus:ring-red-500/30"
            title="Выйти"
            aria-label="Выйти"
          >
            <LogOut className="h-4 w-4" />
            <span className="hidden sm:inline">Выйти</span>
          </button>
        </div>
      </div>
    </header>
  )
}
