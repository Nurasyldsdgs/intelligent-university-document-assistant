import { useEffect, useRef, useState } from 'react'
import { AlertCircle, CheckCircle2, Eye, EyeOff, Loader2 } from 'lucide-react'
import { OTP_LENGTH } from '../constants/otp'

export function TextField({ label, icon: Icon, id, type = 'text', error, ...props }) {
  const [showPassword, setShowPassword] = useState(false)
  const isPassword = type === 'password'

  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">
        {label}
      </label>
      <div className="relative">
        {Icon && (
          <Icon className="pointer-events-none absolute left-3 top-1/2 h-4.5 w-4.5 -translate-y-1/2 text-slate-400 dark:text-slate-500" />
        )}
        <input
          id={id}
          type={isPassword && showPassword ? 'text' : type}
          className={`w-full rounded-lg border bg-white dark:bg-slate-900 py-2.5 text-sm text-slate-900 dark:text-white outline-none transition placeholder:text-slate-400 dark:placeholder:text-slate-500 focus:ring-4 ${
            Icon ? 'pl-10' : 'pl-3'
          } ${isPassword ? 'pr-10' : 'pr-3'} ${
            error
              ? 'border-red-400 focus:border-red-500 focus:ring-red-100 dark:focus:ring-red-500/30'
              : 'border-slate-300 dark:border-slate-700 focus:border-indigo-500 focus:ring-indigo-100 dark:focus:ring-indigo-500/30'
          }`}
          aria-invalid={Boolean(error)}
          {...props}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setShowPassword((value) => !value)}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300"
            aria-label={showPassword ? 'Скрыть пароль' : 'Показать пароль'}
            tabIndex={-1}
          >
            {showPassword ? <EyeOff className="h-4.5 w-4.5" /> : <Eye className="h-4.5 w-4.5" />}
          </button>
        )}
      </div>
      {/* error={true} only outlines the field; the message is shown elsewhere (e.g. "wrong email or password"). */}
      {typeof error === 'string' && error && <p className="mt-1 text-xs text-red-600 dark:text-red-400">{error}</p>}
    </div>
  )
}

export function SubmitButton({ loading, children, ...props }) {
  return (
    <button
      type="submit"
      disabled={loading}
      className="flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 dark:focus:ring-indigo-500/30 disabled:cursor-not-allowed disabled:opacity-70"
      {...props}
    >
      {loading && <Loader2 className="h-4 w-4 animate-spin" />}
      {children}
    </button>
  )
}

export function Alert({ type = 'error', children }) {
  if (!children) return null
  const isError = type === 'error'
  const Icon = isError ? AlertCircle : CheckCircle2
  return (
    <div
      role={isError ? 'alert' : 'status'}
      className={`flex items-start gap-2 rounded-lg border px-3 py-2.5 text-sm ${
        isError
          ? 'border-red-200 dark:border-red-500/30 bg-red-50 dark:bg-red-500/10 text-red-700 dark:text-red-300'
          : 'border-emerald-200 dark:border-emerald-500/30 bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
      }`}
    >
      <Icon className="mt-0.5 h-4 w-4 shrink-0" />
      <span>{children}</span>
    </div>
  )
}

// Six single-digit cells; supports typing, backspace/arrow navigation and pasting the whole code into any cell.
export function OtpInput({ value, onChange, error, disabled, autoFocus }) {
  const refs = useRef([])
  const isEmpty = value.every((digit) => !digit)

  // Focus the first cell on mount and whenever the code is cleared (e.g. after "resend").
  useEffect(() => {
    if (autoFocus && isEmpty) refs.current[0]?.focus()
  }, [autoFocus, isEmpty])

  const handleChange = (index, rawValue) => {
    const digits = rawValue.replace(/\D/g, '')
    if (!digits) {
      onChange(value.map((digit, i) => (i === index ? '' : digit)))
      return
    }
    const next = [...value]
    digits.slice(0, OTP_LENGTH - index).split('').forEach((digit, offset) => {
      next[index + offset] = digit
    })
    onChange(next)
    refs.current[Math.min(index + digits.length, OTP_LENGTH - 1)]?.focus()
  }

  const handleKeyDown = (index, event) => {
    if (event.key === 'Backspace' && !value[index] && index > 0) {
      refs.current[index - 1]?.focus()
    } else if (event.key === 'ArrowLeft' && index > 0) {
      refs.current[index - 1]?.focus()
    } else if (event.key === 'ArrowRight' && index < OTP_LENGTH - 1) {
      refs.current[index + 1]?.focus()
    }
  }

  return (
    <div className={`flex justify-between gap-2 ${error ? 'animate-shake' : ''}`}>
      {value.map((digit, index) => (
        <input
          key={index}
          ref={(element) => {
            refs.current[index] = element
          }}
          value={digit}
          onChange={(event) => handleChange(index, event.target.value)}
          onKeyDown={(event) => handleKeyDown(index, event)}
          onFocus={(event) => event.target.select()}
          inputMode="numeric"
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          maxLength={OTP_LENGTH}
          disabled={disabled}
          aria-label={`Цифра ${index + 1}`}
          aria-invalid={Boolean(error)}
          className={`h-12 w-full rounded-lg border text-center text-lg font-semibold text-slate-900 dark:text-white outline-none transition focus:ring-4 disabled:bg-slate-50 dark:disabled:bg-slate-800/50 ${
            error
              ? 'border-red-400 focus:border-red-500 focus:ring-red-100 dark:focus:ring-red-500/30'
              : digit
                ? 'border-indigo-400 focus:border-indigo-500 focus:ring-indigo-100 dark:focus:ring-indigo-500/30'
                : 'border-slate-300 dark:border-slate-700 focus:border-indigo-500 focus:ring-indigo-100 dark:focus:ring-indigo-500/30'
          }`}
        />
      ))}
    </div>
  )
}
