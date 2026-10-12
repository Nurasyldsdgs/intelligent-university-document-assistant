import { useEffect, useState } from 'react'
import { ArrowLeft, KeyRound, Lock, Mail, RotateCw, ShieldCheck, X } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { getErrorMessage } from '../api/axios'
import { Alert, OtpInput, SubmitButton, TextField } from './FormControls'
import { emptyOtp } from '../constants/otp'

const RESEND_COOLDOWN_SECONDS = 60
const STEPS = [
  { title: 'Email', icon: Mail },
  { title: 'Код', icon: ShieldCheck },
  { title: 'Пароль', icon: KeyRound },
]

export default function ForgotPasswordModal({ initialEmail = '', onClose, onSuccess }) {
  const { forgotPassword, verifyOtp, resetPassword } = useAuth()

  const [step, setStep] = useState(0)
  const [email, setEmail] = useState(initialEmail)
  const [otp, setOtp] = useState(emptyOtp)
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [loading, setLoading] = useState(false)
  const [cooldown, setCooldown] = useState(0)

  const otpValue = otp.join('')

  useEffect(() => {
    const onKeyDown = (event) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  useEffect(() => {
    if (cooldown <= 0) return undefined
    const timer = setTimeout(() => setCooldown((value) => value - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const run = async (action) => {
    setError('')
    setInfo('')
    setLoading(true)
    try {
      await action()
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }

  const requestCode = () => run(async () => {
    await forgotPassword(email.trim())
    setOtp(emptyOtp())
    setCooldown(RESEND_COOLDOWN_SECONDS)
    setStep(1)
    setInfo('Если аккаунт с таким email существует, мы отправили на него 6-значный код.')
  })

  const handleEmailSubmit = (event) => {
    event.preventDefault()
    if (!email.trim()) {
      setError('Введите email')
      return
    }
    requestCode()
  }

  const handleOtpSubmit = (event) => {
    event.preventDefault()
    if (!/^\d{6}$/.test(otpValue)) {
      setError('Введите все 6 цифр кода')
      return
    }
    run(async () => {
      await verifyOtp(email.trim(), otpValue)
      setStep(2)
    })
  }

  const handlePasswordSubmit = (event) => {
    event.preventDefault()
    if (newPassword.length < 8) {
      setError('Пароль должен содержать минимум 8 символов')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('Пароли не совпадают')
      return
    }
    run(async () => {
      await resetPassword(email.trim(), otpValue, newPassword)
      onSuccess(email.trim())
    })
  }

  const goBack = () => {
    setError('')
    setInfo('')
    setStep((value) => value - 1)
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="reset-title"
        className="w-full max-w-md rounded-2xl bg-white dark:bg-slate-900 p-6 shadow-2xl"
      >
        <div className="mb-5 flex items-start justify-between">
          <div>
            <h2 id="reset-title" className="text-lg font-semibold text-slate-900 dark:text-white">
              Восстановление пароля
            </h2>
            <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">Шаг {step + 1} из {STEPS.length}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 dark:text-slate-500 transition hover:bg-slate-100 dark:hover:bg-slate-800 hover:text-slate-600 dark:hover:text-slate-300"
            aria-label="Закрыть"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <ol className="mb-6 flex items-center gap-2">
          {STEPS.map(({ title, icon: Icon }, index) => (
            <li key={title} className="flex flex-1 items-center gap-2">
              <span
                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm transition ${
                  index <= step ? 'bg-indigo-600 text-white' : 'bg-slate-100 dark:bg-slate-800 text-slate-400 dark:text-slate-500'
                }`}
              >
                <Icon className="h-4 w-4" />
              </span>
              <span className={`text-xs font-medium ${index <= step ? 'text-slate-900 dark:text-white' : 'text-slate-400 dark:text-slate-500'}`}>
                {title}
              </span>
              {index < STEPS.length - 1 && (
                <span className={`h-px flex-1 ${index < step ? 'bg-indigo-600' : 'bg-slate-200 dark:bg-slate-700'}`} />
              )}
            </li>
          ))}
        </ol>

        <div className="space-y-4">
          <Alert type="success">{info}</Alert>
          <Alert>{error}</Alert>

          {step === 0 && (
            <form onSubmit={handleEmailSubmit} className="space-y-4" noValidate>
              <p className="text-sm text-slate-600 dark:text-slate-300">
                Укажите email, привязанный к аккаунту. Мы отправим на него код подтверждения.
              </p>
              <TextField
                id="reset-email"
                label="Email"
                type="email"
                icon={Mail}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="student@university.edu"
                autoComplete="email"
                autoFocus
              />
              <SubmitButton loading={loading}>Отправить код</SubmitButton>
            </form>
          )}

          {step === 1 && (
            <form onSubmit={handleOtpSubmit} className="space-y-4" noValidate>
              <p className="text-sm text-slate-600 dark:text-slate-300">
                Введите 6-значный код из письма, отправленного на <b className="text-slate-900 dark:text-white">{email}</b>.
              </p>
              <OtpInput value={otp} onChange={setOtp} autoFocus />
              <SubmitButton loading={loading}>Подтвердить код</SubmitButton>
              <div className="flex items-center justify-between text-sm">
                <button
                  type="button"
                  onClick={goBack}
                  className="flex items-center gap-1 text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200"
                >
                  <ArrowLeft className="h-4 w-4" /> Изменить email
                </button>
                <button
                  type="button"
                  onClick={requestCode}
                  disabled={cooldown > 0 || loading}
                  className="flex items-center gap-1 font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 disabled:cursor-not-allowed disabled:text-slate-400 dark:disabled:text-slate-500"
                >
                  <RotateCw className="h-4 w-4" />
                  {cooldown > 0 ? `Повторить через ${cooldown} с` : 'Отправить снова'}
                </button>
              </div>
            </form>
          )}

          {step === 2 && (
            <form onSubmit={handlePasswordSubmit} className="space-y-4" noValidate>
              <TextField
                id="new-password"
                label="Новый пароль"
                type="password"
                icon={Lock}
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                placeholder="Минимум 8 символов"
                autoComplete="new-password"
                autoFocus
              />
              <TextField
                id="confirm-password"
                label="Повторите пароль"
                type="password"
                icon={Lock}
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                autoComplete="new-password"
              />
              <SubmitButton loading={loading}>Сохранить пароль</SubmitButton>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}
