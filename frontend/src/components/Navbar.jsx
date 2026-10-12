import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { Building2, FileText, FolderLock, GraduationCap, LayoutDashboard, LogOut, Menu, Moon, Sun, User, X } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { DEFAULT_ROLE_BADGE_STYLE, ROLES, ROLE_ADMIN, ROLE_BADGE_STYLES, roleLabel as labelOfRole } from '../constants/roles'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Панель', icon: LayoutDashboard },
  { to: '/documents', label: 'Документы', icon: FolderLock },
  { to: '/courses', label: 'Курсы', icon: GraduationCap },
  { to: '/admin', label: 'Деканат', icon: Building2, adminOnly: true },
]

const FOCUSABLE = 'a[href], button:not([disabled])'

/**
 * A disclosure panel (burger menu, user menu). It closes on Escape (focus returns to its button), on a click outside,
 * when focus tabs out of it, and on navigation: it is tied to the location it was opened at. Opening moves focus to the
 * first item of the panel.
 */
function usePopover() {
  const location = useLocation()
  const [openAt, setOpenAt] = useState(null)
  const open = openAt === location.key
  const rootRef = useRef(null)
  const buttonRef = useRef(null)
  const panelRef = useRef(null)

  const close = useCallback((restoreFocus) => {
    setOpenAt(null)
    if (restoreFocus) buttonRef.current?.focus()
  }, [])

  useEffect(() => {
    if (!open) return undefined
    panelRef.current?.querySelector(FOCUSABLE)?.focus()
    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        close(true)
      }
    }
    const onPointerDown = (event) => {
      if (!rootRef.current?.contains(event.target)) close(false)
    }
    document.addEventListener('keydown', onKeyDown)
    document.addEventListener('pointerdown', onPointerDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.removeEventListener('pointerdown', onPointerDown)
    }
  }, [open, close])

  return {
    open,
    rootRef,
    buttonRef,
    panelRef,
    close,
    toggle: () => (open ? close(true) : setOpenAt(location.key)),
    // Tabbing out of the menu closes it; a click on its padding (no new focus target) does not.
    onBlur: (event) => {
      if (event.relatedTarget && !rootRef.current?.contains(event.relatedTarget)) close(false)
    },
  }
}

export default function Navbar() {
  const { user, role, logout } = useAuth()
  const navigate = useNavigate()

  const items = NAV_ITEMS.filter((item) => !item.adminOnly || user?.role === ROLE_ADMIN)

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const navLinkClass = ({ isActive }) => (
    `flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-200 focus:outline-none focus-visible:outline-2 focus-visible:outline-solid focus-visible:outline-offset-2 focus-visible:outline-indigo-500 ${
      isActive
        ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 shadow-sm ring-1 ring-inset ring-indigo-100 dark:ring-indigo-500/30'
        : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100/80 dark:hover:bg-slate-800/80 hover:text-slate-900 dark:hover:text-white'
    }`
  )

  return (
    <header className="sticky top-0 z-50 border-b border-slate-200/80 dark:border-slate-800 bg-white/80 dark:bg-slate-900/80 backdrop-blur-md">
      <div className="mx-auto flex min-h-16 max-w-6xl items-center justify-between gap-3 px-4 py-2 sm:px-6">
        <Link to="/dashboard" className="flex shrink-0 items-center gap-3" aria-label="UniDocs Assistant">
          <div className="relative flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-600 to-violet-600 text-white shadow-lg shadow-indigo-200/70 dark:shadow-none">
            <FileText className="h-5 w-5" />
            <span className="absolute -right-1 -top-1 h-3 w-3 rounded-full border-2 border-white dark:border-slate-900 bg-cyan-400" />
          </div>
          <span className="hidden leading-tight sm:block">
            <span className="block text-sm font-bold text-slate-900 dark:text-white">UniDocs</span>
            <span className="block text-xs font-medium text-slate-500 dark:text-slate-400">Assistant</span>
          </span>
        </Link>

        <div className="flex min-w-0 items-center gap-2">
          {/* lg and up: icons; the labels appear from xl. */}
          <nav aria-label="Основная навигация" className="hidden items-center gap-1 lg:flex xl:gap-2">
            {items.map(({ to, label, icon: Icon }) => (
              <NavLink key={to} to={to} className={navLinkClass} aria-label={label} title={label}>
                <Icon className="h-4 w-4 shrink-0" />
                <span className="hidden xl:inline">{label}</span>
              </NavLink>
            ))}
          </nav>
          {user && <UserMenu user={user} role={role} onLogout={handleLogout} />}
          <BurgerMenu items={items} navLinkClass={navLinkClass} />
        </div>
      </div>
    </header>
  )
}

/** Below lg: the navigation in a panel under the header. */
function BurgerMenu({ items, navLinkClass }) {
  const panelId = useId()
  const { open, rootRef, buttonRef, panelRef, close, toggle, onBlur } = usePopover()

  return (
    <div ref={rootRef} onBlur={onBlur} className="lg:hidden">
      <button
        ref={buttonRef}
        type="button"
        onClick={toggle}
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={open ? 'Закрыть меню' : 'Открыть меню'}
        title={open ? 'Закрыть меню' : 'Меню'}
        className="flex h-10 w-10 items-center justify-center rounded-lg text-slate-600 dark:text-slate-300 transition hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-white focus:outline-none focus-visible:ring-4 focus-visible:ring-indigo-100 dark:focus-visible:ring-indigo-500/30"
      >
        {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
      </button>
      {open && (
        <nav
          ref={panelRef}
          id={panelId}
          aria-label="Основная навигация"
          className="absolute inset-x-0 top-full border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 shadow-lg"
        >
          <ul className="mx-auto max-w-6xl space-y-1 px-4 py-3 sm:px-6">
            {items.map(({ to, label, icon: Icon }) => (
              <li key={to}>
                <NavLink to={to} className={navLinkClass} onClick={() => close(false)}>
                  <Icon className="h-4 w-4 shrink-0" />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </div>
  )
}

/** The avatar (initials + role icon); its menu holds the name, email, role, the theme switch and "Выйти". */
function UserMenu({ user, role, onLogout }) {
  const panelId = useId()
  const { isDark, toggleTheme } = useTheme()
  const { open, rootRef, buttonRef, panelRef, toggle, onBlur } = usePopover()

  const roleInfo = ROLES.find((item) => item.value === role)
  const RoleIcon = roleInfo?.icon ?? User
  const roleLabel = labelOfRole(role)
  const badgeStyle = ROLE_BADGE_STYLES[role] ?? DEFAULT_ROLE_BADGE_STYLE
  const initials = (user.fullName || user.email || 'U')
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  const itemClass = 'flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-sm font-medium transition focus:outline-none focus-visible:ring-4'

  return (
    <div ref={rootRef} onBlur={onBlur} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={toggle}
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={`Профиль: ${user.fullName || user.email}${roleLabel ? `, ${roleLabel}` : ''}`}
        title={user.fullName || user.email}
        className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-indigo-100 dark:from-indigo-500/20 to-violet-100 dark:to-violet-500/20 text-xs font-bold text-indigo-700 dark:text-indigo-300 ring-2 ring-white dark:ring-slate-900 transition hover:ring-indigo-200 dark:hover:ring-indigo-500/40 focus:outline-none focus-visible:ring-4 focus-visible:ring-indigo-200 dark:focus-visible:ring-indigo-500/40"
      >
        {initials}
        <span className={`absolute -bottom-0.5 -right-0.5 flex h-4.5 w-4.5 items-center justify-center rounded-full ring-2 ring-white dark:ring-slate-900 ${badgeStyle}`}>
          <RoleIcon className="h-2.5 w-2.5" />
        </span>
      </button>
      {open && (
        <div
          ref={panelRef}
          id={panelId}
          className="absolute right-0 top-full z-50 mt-2 w-72 max-w-[calc(100vw-2rem)] rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 p-2 shadow-xl"
        >
          <div className="border-b border-slate-100 dark:border-slate-800 px-3 pb-3 pt-2">
            <p className="truncate text-sm font-semibold text-slate-900 dark:text-white" title={user.fullName || undefined}>
              {user.fullName || user.email}
            </p>
            {user.fullName && (
              <p className="truncate text-xs text-slate-600 dark:text-slate-300" title={user.email}>{user.email}</p>
            )}
            <span className={`mt-2 inline-flex items-center gap-1 rounded-full px-2 py-1 text-xs font-semibold ring-1 ring-inset ${badgeStyle}`}>
              <RoleIcon className="h-3 w-3" />
              {roleLabel}
            </span>
          </div>
          <div className="space-y-0.5 pt-2">
            <button
              type="button"
              onClick={toggleTheme}
              className={`${itemClass} text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 focus-visible:ring-indigo-100 dark:focus-visible:ring-indigo-500/30`}
            >
              {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
              {isDark ? 'Светлая тема' : 'Тёмная тема'}
            </button>
            <button
              type="button"
              onClick={onLogout}
              className={`${itemClass} text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-500/10 focus-visible:ring-red-100 dark:focus-visible:ring-red-500/30`}
            >
              <LogOut className="h-4 w-4" />
              Выйти
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
