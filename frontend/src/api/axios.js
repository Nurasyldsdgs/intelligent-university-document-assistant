import axios from 'axios'

// Backend origin; override with VITE_BACKEND_URL (e.g. in .env.local or for a deployment).
const BACKEND_URL = import.meta.env.VITE_BACKEND_URL ?? 'http://localhost:8080'
export const API_BASE_URL = `${BACKEND_URL}/api/v1`
export const GOOGLE_AUTH_URL = `${BACKEND_URL}/oauth2/authorization/google`
export const TOKEN_KEY = 'token'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// A 401 on a protected endpoint means the token is expired or revoked: tell AuthContext to drop the session.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthEndpoint = error.config?.url?.startsWith('/auth/')
    if (error.response?.status === 401 && !isAuthEndpoint) {
      localStorage.removeItem(TOKEN_KEY)
      window.dispatchEvent(new Event('auth:logout'))
    }
    return Promise.reject(error)
  },
)

// Backend messages are in English; map the known ones to Russian for the UI.
const MESSAGE_TRANSLATIONS = {
  'Invalid email or password': 'Неверный email или пароль',
  'An account with this email already exists': 'Аккаунт с таким email уже существует',
  'ADMIN role cannot be self-assigned':
    'Роль «Деканат» нельзя выбрать при самостоятельной регистрации. Обратитесь к администратору системы.',
  'This account uses Google sign-in. Please log in with Google.':
    'Этот аккаунт зарегистрирован через Google. Войдите через Google.',
  'Invalid or expired code': 'Неверный или просроченный код',
  'Code has expired. Please request a new one.': 'Срок действия кода истёк. Запросите новый.',
  'Too many failed attempts. Please request a new code.':
    'Слишком много неудачных попыток. Запросите новый код.',
  'New password must differ from the current one': 'Новый пароль должен отличаться от текущего',
  'Could not send email. Please try again later.': 'Не удалось отправить письмо. Попробуйте позже.',
  'Malformed request body or invalid field value': 'Некорректные данные запроса',
  'Request conflicts with existing data': 'Такие данные уже существуют',
  'Access denied': 'Доступ запрещён',
  'Document not found': 'Документ не найден',
  'File is empty': 'Файл пустой',
  'File is too large': 'Файл слишком большой (максимум 20 МБ)',
  'Preview is not available for this file type': 'Предпросмотр недоступен для этого формата',
  'User not found': 'Пользователь не найден',
  'You cannot change your own role': 'Нельзя изменить собственную роль',
  'You cannot delete your own account': 'Нельзя удалить собственный аккаунт',
  'Course not found': 'Курс не найден',
  'Task not found': 'Задача не найдена',
  'Only TEACHER or ADMIN can upload TEACHER_ONLY documents':
    'Видимость «Только преподаватели» доступна только преподавателям и деканату',
  'courseId is required for COURSE_STUDENTS visibility': 'Для видимости «Студенты курса» выберите курс',
  'You are not a member of this course': 'Вы не состоите в этом курсе',
  'Only students can enroll in a course': 'Записаться на курс может только студент',
  'Only the course teacher or ADMIN can enroll other users': 'Записывать других может только преподаватель курса',
  'Only the owner, the course teacher or ADMIN can analyze this document':
    'Запустить анализ может владелец документа, преподаватель курса или деканат',
  'The document has no extracted text to analyze': 'В документе нет распознанного текста для анализа',
  'You can change only your own tasks': 'Можно менять только свои задачи',
  'AI assistant is not configured': 'ИИ-ассистент не подключён на сервере. Обратитесь к администратору.',
  'AI analysis is not configured': 'ИИ-ассистент не подключён на сервере. Обратитесь к администратору.',
  'AI assistant is busy, try again later': 'ИИ-ассистент сейчас перегружен. Попробуйте через минуту.',
  'AI analysis queue is full, try again later': 'Очередь ИИ-анализа заполнена. Попробуйте через минуту.',
}

export const AI_UNAVAILABLE_MESSAGE = 'ИИ-ассистент временно недоступен. Попробуйте позже.'
export const FORBIDDEN_MESSAGE = 'У вас нет прав доступа к этому ресурсу.'

/**
 * Message for any failed request. 503 (AI off, down or overloaded) and 403 get dedicated wording; the backend's
 * own message is used when it is more specific (e.g. "not configured" vs. "busy").
 */
export function describeError(error) {
  const status = error?.response?.status
  const serverMessage = error?.response?.data?.message
  if (status === 503) {
    return { status, message: MESSAGE_TRANSLATIONS[serverMessage] ?? AI_UNAVAILABLE_MESSAGE }
  }
  if (status === 403) {
    const known = MESSAGE_TRANSLATIONS[serverMessage]
    return { status, message: known && serverMessage !== 'Access denied' ? known : FORBIDDEN_MESSAGE }
  }
  return { status, message: getErrorMessage(error) }
}

const FIELD_LABELS = {
  email: 'Email',
  password: 'Пароль',
  newPassword: 'Новый пароль',
  fullName: 'ФИО',
  otp: 'Код',
  role: 'Роль',
}

export function getErrorMessage(error) {
  if (!error?.response) {
    return error?.code === 'ECONNABORTED'
      ? 'Сервер не ответил вовремя. Попробуйте ещё раз.'
      : 'Сервер недоступен. Проверьте, что бэкенд запущен.'
  }

  const { status, data } = error.response

  if (data?.fieldErrors) {
    return Object.entries(data.fieldErrors)
      .map(([field, message]) => `${FIELD_LABELS[field] ?? field}: ${message}`)
      .join('; ')
  }
  if (data?.message) {
    return MESSAGE_TRANSLATIONS[data.message] ?? data.message
  }
  if (status >= 500) {
    return 'Внутренняя ошибка сервера. Попробуйте позже.'
  }
  return 'Что-то пошло не так. Попробуйте ещё раз.'
}

// With responseType "blob" the error body is a Blob too; parse it so getErrorMessage sees the JSON message.
export async function getBlobErrorMessage(error) {
  const data = error.response?.data
  if (data instanceof Blob) {
    try {
      error.response.data = JSON.parse(await data.text())
    } catch {
      error.response.data = null
    }
  }
  return getErrorMessage(error)
}

export default api
