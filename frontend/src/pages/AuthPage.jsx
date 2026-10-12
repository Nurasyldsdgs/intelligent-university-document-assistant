import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Check, FileText, Lock, Mail, ShieldCheck, Sparkles, User, Users } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { GOOGLE_AUTH_URL, getErrorMessage, messageForCode } from '../api/axios'
import { ROLE_STUDENT, ROLE_TEACHER } from '../constants/roles'
import { Alert, SubmitButton, TextField } from '../components/FormControls'
import ForgotPasswordModal from '../components/ForgotPasswordModal'
import ThemeToggle from '../components/ThemeToggle'
import EmailVerificationStep from '../components/EmailVerificationStep'
import { pathFromLocationState, rememberPostLoginRedirect } from '../utils/postLoginRedirect'

const OAUTH_ERRORS = {
  oauth2_failed: 'Не удалось войти через Google. Попробуйте ещё раз.',
  email_not_provided: 'Google не передал email. Разрешите доступ к email и попробуйте снова.',
  profile_failed: 'Не удалось загрузить профиль после входа через Google.',
}

const RESEND_COOLDOWN_SECONDS = 60

// Everyone registers as a student; teacherRequested asks the dean's office (ADMIN) for the teacher role.
const EMPTY_FORM = { email: '', password: '', fullName: '', teacherRequested: false }

const MIN_PASSWORD_LENGTH = 8 // RegisterRequest: @Size(min = 8)
const MISSING_REQUIRED = 'Заполните все обязательные поля.'
const PASSWORD_TOO_SHORT = `Пароль слишком короткий. Минимальная длина — ${MIN_PASSWORD_LENGTH} символов.`
const REQUIRED_MESSAGES = { email: 'Введите email', password: 'Введите пароль', fullName: 'Введите ФИО' }

// Same rules and wording as the backend, so most mistakes are caught before the request is sent.
function validate(form, isRegister) {
  const errors = {}
  if (!form.email.trim()) errors.email = REQUIRED_MESSAGES.email
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) errors.email = 'Введите корректный email'

  if (!form.password) errors.password = REQUIRED_MESSAGES.password
  else if (isRegister && form.password.length < MIN_PASSWORD_LENGTH) errors.password = PASSWORD_TOO_SHORT

  if (isRegister && !form.fullName.trim()) errors.fullName = REQUIRED_MESSAGES.fullName
  return errors
}

/**
 * The form's reaction to a failed login/register request: the message for the alert above the submit button and the
 * fields to outline in red (true outlines a field without a message of its own).
 */
function describeAuthError(err) {
  const data = err.response?.data
  switch (data?.code) {
    case 'EMAIL_TAKEN':
      return { message: messageForCode(data), fields: { email: true } }
    case 'INVALID_CREDENTIALS':
      return { message: messageForCode(data), fields: { email: true, password: true } }
    case 'VALIDATION_FAILED':
      // The server's summary; each field also gets its own message below it.
      return { message: data.message, fields: data.fieldErrors ?? {} }
    default:
      return { message: getErrorMessage(err), fields: {} }
  }
}

export default function AuthPage() {
  const { login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [mode, setMode] = useState('login')
  const [form, setForm] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(() => OAUTH_ERRORS[location.state?.oauthError] ?? '')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)
  // After a 429 from login: { email, until (ms), message }. The server blocks this email from this browser's IP,
  // so the button stays disabled for that email until the block ends.
  const [loginLock, setLoginLock] = useState(null)
  const [showReset, setShowReset] = useState(false)
  // Set once an unverified account needs its email confirmed: { email, info, cooldown }.
  const [verification, setVerification] = useState(null)

  const isRegister = mode === 'register'

  const switchMode = (nextMode) => {
    setMode(nextMode)
    setFieldErrors({})
    setError('')
    setSuccess('')
  }

  // Typing again clears the alert and this field's outline: the old message no longer describes the input.
  const updateField = (field) => (event) => {
    setForm((prev) => ({ ...prev, [field]: event.target.value }))
    setFieldErrors((prev) => ({ ...prev, [field]: undefined }))
    setError('')
  }

  useEffect(() => {
    if (!loginLock) return undefined
    const timer = setTimeout(() => setLoginLock(null), Math.max(0, loginLock.until - Date.now()))
    return () => clearTimeout(timer)
  }, [loginLock])

  const loginLocked = !isRegister && loginLock !== null && form.email.trim().toLowerCase() === loginLock.email

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    setSuccess('')

    const errors = validate(form, isRegister)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      const missing = Object.entries(errors).some(([field, message]) => REQUIRED_MESSAGES[field] === message)
      setError(missing ? MISSING_REQUIRED : Object.values(errors)[0])
      return
    }

    const email = form.email.trim()
    setLoading(true)
    try {
      if (isRegister) {
        const message = await register({
          email,
          password: form.password,
          fullName: form.fullName.trim(),
          role: form.teacherRequested ? ROLE_TEACHER : ROLE_STUDENT,
        })
        setVerification({ email, info: message, cooldown: RESEND_COOLDOWN_SECONDS })
      } else {
        await login(email, form.password)
        navigate(pathFromLocationState(location.state) ?? '/dashboard', { replace: true })
      }
    } catch (err) {
      if (err.response?.data?.code === 'EMAIL_NOT_VERIFIED') {
        // Login does not send a new code: the one from registration may still be valid, otherwise resend.
        setVerification({
          email,
          info: 'Email ещё не подтверждён. Введите код из письма или запросите новый.',
          cooldown: 0,
        })
      } else {
        const { message, fields } = describeAuthError(err)
        setError(message)
        setFieldErrors(fields)
        if (!isRegister && err.response?.status === 429) {
          const seconds = Number(err.response.data?.retryAfterSeconds) || 15 * 60
          setLoginLock({ email: email.toLowerCase(), until: Date.now() + seconds * 1000, message })
        }
      }
    } finally {
      setLoading(false)
    }
  }

  const handleVerificationBack = () => {
    setVerification(null)
    setError('')
    setSuccess('')
  }

  const handleResetSuccess = (email) => {
    setShowReset(false)
    setMode('login')
    setForm({ ...EMPTY_FORM, email })
    setError('')
    setSuccess('Пароль успешно изменён. Войдите с новым паролем.')
  }

  return (
    <div className="flex min-h-screen bg-slate-50 dark:bg-slate-950">
      <aside className="relative hidden w-1/2 overflow-hidden bg-gradient-to-br from-indigo-700 via-indigo-600 to-violet-600 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/10 blur-3xl" />
        <div className="absolute -bottom-32 -left-16 h-96 w-96 rounded-full bg-violet-400/20 blur-3xl" />

        <div className="relative flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/15 backdrop-blur">
            <FileText className="h-5 w-5" />
          </div>
          <span className="text-lg font-semibold">UniDocs Assistant</span>
        </div>

        <div className="relative max-w-md">
          <h1 className="text-4xl font-bold leading-tight">
            Интеллектуальный помощник по университетским документам
          </h1>
          <p className="mt-4 text-indigo-100">
            Справки, заявления и учебные документы — в одном месте, без очередей в деканат.
          </p>
          <ul className="mt-8 space-y-4">
            {[
              { icon: Sparkles, text: 'Умный поиск и заполнение документов' },
              { icon: Users, text: 'Студенты, преподаватели и деканат на одной платформе' },
              { icon: ShieldCheck, text: 'Безопасный вход через университетский email или Google' },
            ].map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-indigo-50">
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-white/15">
                  <Icon className="h-4 w-4" />
                </span>
                {text}
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-sm text-indigo-200">© {new Date().getFullYear()} UniDocs Assistant</p>
      </aside>

      <main className="relative flex w-full items-center justify-center px-4 py-10 lg:w-1/2">
        <ThemeToggle className="absolute right-4 top-4" />
        <div className="w-full max-w-md">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 text-white">
              <FileText className="h-5 w-5" />
            </div>
            <span className="text-lg font-semibold text-slate-900 dark:text-white">UniDocs Assistant</span>
          </div>

          {isRegister && <RegisterSteps current={verification ? 1 : 0} />}

          {verification ? (
            <EmailVerificationStep
              key={verification.email}
              email={verification.email}
              initialInfo={verification.info}
              initialCooldown={verification.cooldown}
              onBack={handleVerificationBack}
            />
          ) : (
            <div key={mode} className="animate-step-in">
            <h2 className="text-2xl font-bold text-slate-900 dark:text-white">
              {isRegister ? 'Создайте аккаунт' : 'С возвращением!'}
            </h2>
            <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {isRegister ? 'Заполните данные для регистрации' : 'Войдите, чтобы продолжить работу'}
            </p>

            <div className="mt-6 grid grid-cols-2 rounded-xl bg-slate-200/70 dark:bg-slate-800 p-1" role="tablist">
              {[
                { value: 'login', label: 'Вход' },
                { value: 'register', label: 'Регистрация' },
              ].map(({ value, label }) => (
                <button
                  key={value}
                  type="button"
                  role="tab"
                  aria-selected={mode === value}
                  onClick={() => switchMode(value)}
                  className={`rounded-lg py-2 text-sm font-medium transition ${
                    mode === value ? 'bg-white dark:bg-slate-900 text-slate-900 dark:text-white shadow-sm' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>

            <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
              <Alert type="success">{success}</Alert>

              {isRegister && (
                <TextField
                  id="fullName"
                  label="ФИО"
                  icon={User}
                  value={form.fullName}
                  onChange={updateField('fullName')}
                  placeholder="Иванов Иван Иванович"
                  autoComplete="name"
                  error={fieldErrors.fullName}
                />
              )}

              <TextField
                id="email"
                label="Email"
                type="email"
                icon={Mail}
                value={form.email}
                onChange={updateField('email')}
                placeholder="student@university.edu"
                autoComplete="email"
                error={fieldErrors.email}
              />

              <div>
                <TextField
                  id="password"
                  label="Пароль"
                  type="password"
                  icon={Lock}
                  value={form.password}
                  onChange={updateField('password')}
                  placeholder={isRegister ? `Минимум ${MIN_PASSWORD_LENGTH} символов` : '••••••••'}
                  autoComplete={isRegister ? 'new-password' : 'current-password'}
                  error={fieldErrors.password}
                />
                {!isRegister && (
                  <div className="mt-2 text-right">
                    <button
                      type="button"
                      onClick={() => setShowReset(true)}
                      className="text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300"
                    >
                      Забыли пароль?
                    </button>
                  </div>
                )}
              </div>

              {isRegister && (
                <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2.5 text-sm text-slate-700 dark:text-slate-200 transition hover:border-slate-400">
                  <input
                    type="checkbox"
                    checked={form.teacherRequested}
                    onChange={(event) => setForm((current) => ({ ...current, teacherRequested: event.target.checked }))}
                    className="mt-0.5 h-4 w-4 shrink-0 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                  />
                  <span>
                    Я преподаватель (роль подтверждает деканат)
                    {form.teacherRequested && (
                      <span className="mt-0.5 block text-xs text-slate-500 dark:text-slate-400">
                        До подтверждения вы входите как студент; деканат увидит заявку в своей панели.
                      </span>
                    )}
                  </span>
                </label>
              )}

              {error && (
                // Keyed by the text, so a new error shakes again even when the alert is already shown.
                <div key={error} className="animate-shake">
                  <Alert>{error}</Alert>
                </div>
              )}

              {loginLocked && !error && (
                // The alert above is cleared by typing; the reason for the disabled button must stay visible.
                <p className="text-center text-xs text-amber-700 dark:text-amber-300" role="status">{loginLock.message}</p>
              )}
              <SubmitButton loading={loading} disabled={loading || loginLocked}>
                {isRegister ? 'Зарегистрироваться' : loginLocked ? 'Вход временно заблокирован' : 'Войти'}
              </SubmitButton>
            </form>

            <div className="my-6 flex items-center gap-3 text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400">
              <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
              или
              <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
            </div>

            <a
              href={GOOGLE_AUTH_URL}
              onClick={() => rememberPostLoginRedirect(pathFromLocationState(location.state))}
              className="flex w-full items-center justify-center gap-3 rounded-lg border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900 px-4 py-2.5 text-sm font-semibold text-slate-700 dark:text-slate-200 shadow-sm transition hover:bg-slate-50 dark:hover:bg-slate-800/50 focus:outline-none focus:ring-4 focus:ring-slate-100"
            >
              <GoogleIcon />
              Войти через Google
            </a>
            </div>
          )}
        </div>
      </main>

      {showReset && (
        <ForgotPasswordModal
          initialEmail={form.email.trim()}
          onClose={() => setShowReset(false)}
          onSuccess={handleResetSuccess}
        />
      )}
    </div>
  )
}

const REGISTER_STEPS = ['Регистрация', 'Подтверждение email']

function RegisterSteps({ current }) {
  return (
    <ol className="mb-6 flex items-center gap-2" aria-label={`Шаг ${current + 1} из ${REGISTER_STEPS.length}`}>
      {REGISTER_STEPS.map((title, index) => {
        const done = index < current
        const active = index <= current
        return (
          <li key={title} className="flex flex-1 items-center gap-2">
            <span
              className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-colors duration-300 ${
                active ? 'bg-indigo-600 text-white' : 'bg-slate-200 dark:bg-slate-700 text-slate-500 dark:text-slate-400'
              }`}
            >
              {done ? <Check className="h-4 w-4" /> : index + 1}
            </span>
            <span
              className={`text-xs font-medium transition-colors duration-300 ${
                active ? 'text-slate-900 dark:text-white' : 'text-slate-500 dark:text-slate-400'
              }`}
            >
              {title}
            </span>
            {index < REGISTER_STEPS.length - 1 && (
              <span className="relative h-0.5 flex-1 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                <span
                  className={`absolute inset-y-0 left-0 bg-indigo-600 transition-all duration-500 ${
                    done ? 'w-full' : 'w-0'
                  }`}
                />
              </span>
            )}
          </li>
        )
      })}
    </ol>
  )
}

function GoogleIcon() {
  return (
    <svg className="h-5 w-5" viewBox="0 0 48 48" aria-hidden="true">
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3c-1.6 4.7-6.1 8-11.3 8-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z" />
      <path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z" />
    </svg>
  )
}
