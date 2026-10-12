import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import Toast from '../components/Toast'
import { describeError } from '../api/axios'

const ToastContext = createContext(null)

// App-wide notifications: one Toast at a time, the newest replaces the previous one.
export function ToastProvider({ children }) {
  const [toast, setToast] = useState(null)
  const close = useCallback(() => setToast(null), [])

  const value = useMemo(() => {
    const show = (type, message) => setToast((prev) => ({ id: (prev?.id ?? 0) + 1, type, message }))
    return {
      showSuccess: (message) => show('success', message),
      showError: (message) => show('error', message),
      // Shows the right wording for a failed request (503 -> AI unavailable, 403 -> no access) and returns it.
      notifyError: (error) => {
        const { message } = describeError(error)
        show('error', message)
        return message
      },
    }
  }, [])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <Toast toast={toast} onClose={close} />
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useToast() {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast must be used inside ToastProvider')
  return context
}
