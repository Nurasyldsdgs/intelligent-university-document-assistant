// Same names as the backend DocumentVisibility enum. Every document belongs to a course; PUBLIC_ALL is a legacy value
// that the backend now treats like COURSE_STUDENTS.
export const VISIBILITY_LABELS = {
  PUBLIC_ALL: 'Студенты курса',
  COURSE_STUDENTS: 'Студенты курса',
  TEACHER_ONLY: 'Только преподаватель курса',
}

// Same names as the backend DocumentCategory enum.
export const CATEGORY_LABELS = {
  SYLLABUS: 'Силлабус',
  ASSIGNMENT: 'Задание',
  LECTURE: 'Лекция',
  TRANSCRIPT: 'Транскрипт',
  THESIS: 'Диссертация / ВКР',
  OTHER: 'Другое',
}

export const CATEGORY_OPTIONS = Object.entries(CATEGORY_LABELS).map(([value, label]) => ({ value, label }))

// Formats the backend accepts in multi-file and ZIP uploads (DocumentService.ALLOWED_TYPES).
export const DOCUMENT_EXTENSIONS = ['pdf', 'docx', 'doc', 'pptx', 'txt', 'png', 'jpg', 'jpeg']

// Categories the backend analyzes with AI right after upload (DocumentAnalysisService.AUTO_ANALYZED).
export const AUTO_ANALYZED_CATEGORIES = new Set(['SYLLABUS', 'ASSIGNMENT'])

/** What the course teacher (or ADMIN) can choose when uploading. */
export const VISIBILITY_OPTIONS = [
  { value: 'COURSE_STUDENTS', label: 'Студенты курса' },
  { value: 'TEACHER_ONLY', label: 'Только преподаватель курса (заметки)' },
]

// Same names as the backend TaskStatus enum.
export const TASK_STATUS_LABELS = {
  PENDING: 'К выполнению',
  IN_PROGRESS: 'В работе',
  COMPLETED: 'Выполнено',
}
