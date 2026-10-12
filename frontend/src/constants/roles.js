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
