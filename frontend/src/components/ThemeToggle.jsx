import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../context/ThemeContext'

// Shows the Sun in the light theme and the Moon in the dark one; click switches to the other theme.
export default function ThemeToggle({ className = '' }) {
  const { isDark, toggleTheme } = useTheme()
  const label = isDark ? 'Включить светлую тему' : 'Включить тёмную тему'

  return (
    <button
      type="button"
      onClick={toggleTheme}
      title={label}
      aria-label={label}
      className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-slate-500 transition-all duration-200 hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-4 focus:ring-indigo-100 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white dark:focus:ring-indigo-500/30 ${className}`}
    >
      {isDark ? <Moon className="h-4.5 w-4.5" /> : <Sun className="h-4.5 w-4.5" />}
    </button>
  )
}
