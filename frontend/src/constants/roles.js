import { Building2, GraduationCap, Presentation } from 'lucide-react'

// Same names as the backend Role enum and the "role" claim in the JWT.
export const ROLE_STUDENT = 'ROLE_STUDENT'
export const ROLE_TEACHER = 'ROLE_TEACHER'
export const ROLE_ADMIN = 'ROLE_ADMIN'

export const ROLES = [
  { value: ROLE_STUDENT, label: 'Студент', icon: GraduationCap },
  { value: ROLE_TEACHER, label: 'Преподаватель', icon: Presentation },
  { value: ROLE_ADMIN, label: 'Деканат', icon: Building2 },
]

export const ALL_ROLES = ROLES.map((role) => role.value)

// Names an old backend build or a stored session may still carry.
const LEGACY_ROLES = { ROLE_FACULTY: ROLE_TEACHER }

// Brings any role string to the ROLE_* form: "STUDENT" -> "ROLE_STUDENT", "ROLE_FACULTY" -> "ROLE_TEACHER".
export function normalizeRole(role) {
  if (!role) return null
  const upper = String(role).trim().toUpperCase()
  const prefixed = upper.startsWith('ROLE_') ? upper : `ROLE_${upper}`
  return LEGACY_ROLES[prefixed] ?? prefixed
}

export const ROLE_LABELS = Object.fromEntries(ROLES.map((role) => [role.value, role.label]))

// Role badge colours (Navbar, admin users table).
export const ROLE_BADGE_STYLES = {
  ROLE_STUDENT: 'bg-sky-50 dark:bg-sky-500/10 text-sky-700 dark:text-sky-300 ring-sky-200 dark:ring-sky-500/30',
  ROLE_TEACHER: 'bg-amber-50 dark:bg-amber-500/10 text-amber-700 dark:text-amber-300 ring-amber-200 dark:ring-amber-500/30',
  ROLE_ADMIN: 'bg-violet-50 dark:bg-violet-500/10 text-violet-700 dark:text-violet-300 ring-violet-200 dark:ring-violet-500/30',
}
export const DEFAULT_ROLE_BADGE_STYLE = 'bg-slate-50 dark:bg-slate-800 text-slate-700 dark:text-slate-200 ring-slate-200 dark:ring-slate-700'

/** The role's Russian name; never the ROLE_* code (an unknown role reads "Неизвестная роль"). */
export const roleLabel = (role) => ROLE_LABELS[role] ?? 'Неизвестная роль'
