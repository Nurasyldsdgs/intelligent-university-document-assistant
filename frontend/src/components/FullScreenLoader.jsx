import { Loader2 } from 'lucide-react'

export default function FullScreenLoader({ text = 'Загрузка…' }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-slate-50 dark:bg-slate-950 text-slate-500 dark:text-slate-400">
      <Loader2 className="h-8 w-8 animate-spin text-indigo-600 dark:text-indigo-400" />
      <p className="text-sm">{text}</p>
    </div>
  )
}
