import { useEffect, useId, useRef, useState } from 'react'
import { ChevronDown, Loader2, RotateCw, Search } from 'lucide-react'
import { getErrorMessage } from '../api/axios'

const DEBOUNCE_MS = 300
const MIN_QUERY = 2

/**
 * A select whose options come from the server: type to search (2+ characters, debounced 300 ms), or open it to see
 * the first page. Combobox pattern: arrows move, Enter picks, Escape closes.
 *
 * @param value        the picked option { value, label } or null
 * @param onChange     receives the picked option
 * @param fetchOptions (query) => Promise<option[]>; query is '' for "first page"
 */
export default function SearchSelect({
  id, value, onChange, fetchOptions, placeholder = 'Выберите…', searchPlaceholder = 'Поиск…', disabled = false,
  ariaLabel, invalid = false, emptyText = 'Ничего не найдено', className = '',
}) {
  const listId = useId()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState({ forQuery: null, options: [], error: '' })
  const [active, setActive] = useState(-1)
  const [attempt, setAttempt] = useState(0)
  const inputRef = useRef(null)
  const rootRef = useRef(null)

  const effectiveQuery = query.trim().length >= MIN_QUERY ? query.trim() : ''
  const tooShort = query.trim().length > 0 && query.trim().length < MIN_QUERY

  // Load while open; a newer query or a close cancels the older request's result.
  useEffect(() => {
    if (!open) return undefined
    let cancelled = false
    const timer = setTimeout(() => {
      fetchOptions(effectiveQuery)
        .then((options) => !cancelled && setResults({ forQuery: effectiveQuery, options, error: '' }))
        .catch((err) => !cancelled && setResults({ forQuery: effectiveQuery, options: [], error: getErrorMessage(err) }))
    }, effectiveQuery ? DEBOUNCE_MS : 0)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [open, effectiveQuery, attempt, fetchOptions])

  // Clicking anywhere else closes the list.
  useEffect(() => {
    if (!open) return undefined
    const onPointerDown = (event) => {
      if (!rootRef.current?.contains(event.target)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  const loading = open && results.forQuery !== effectiveQuery
  const options = loading ? [] : results.options

  const pick = (option) => {
    onChange(option)
    setOpen(false)
    setQuery('')
    setActive(-1)
  }

  const onKeyDown = (event) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      if (!open) setOpen(true)
      setActive((current) => Math.min(options.length - 1, current + 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((current) => Math.max(0, current - 1))
    } else if (event.key === 'Enter' && open) {
      event.preventDefault()
      if (options[active]) pick(options[active])
    } else if (event.key === 'Escape' && open) {
      event.preventDefault()
      setOpen(false)
    }
  }

  return (
    <div ref={rootRef} className={`relative ${className}`}>
      <div className="relative">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-500 dark:text-slate-400" />
        <input
          ref={inputRef}
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && active >= 0 ? `${listId}-${active}` : undefined}
          aria-label={ariaLabel}
          aria-invalid={invalid || undefined}
          disabled={disabled}
          value={open ? query : value?.label ?? ''}
          placeholder={open ? searchPlaceholder : placeholder}
          onFocus={() => setOpen(true)}
          onClick={() => setOpen(true)}
          onChange={(event) => {
            setQuery(event.target.value)
            setOpen(true)
            setActive(-1)
          }}
          onKeyDown={onKeyDown}
          className={`w-full truncate rounded-lg border bg-white dark:bg-slate-900 py-2 pl-8 pr-8 text-sm text-slate-900 dark:text-white outline-none transition placeholder:text-slate-500 dark:placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100 dark:focus:ring-indigo-500/30 disabled:opacity-60 ${
            invalid ? 'border-amber-400' : 'border-slate-300 dark:border-slate-700'
          }`}
        />
        <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500 dark:text-slate-400" />
      </div>
      {open && (
        <ul
          id={listId}
          role="listbox"
          aria-label={ariaLabel}
          className="absolute z-30 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 py-1 text-sm shadow-lg"
        >
          {tooShort && <li className="px-3 py-2 text-xs text-slate-600 dark:text-slate-300">Введите ещё хотя бы один символ</li>}
          {loading ? (
            <li className="flex items-center gap-2 px-3 py-2 text-xs text-slate-600 dark:text-slate-300"><Loader2 className="h-3.5 w-3.5 animate-spin" /> Загрузка…</li>
          ) : results.error ? (
            <li className="flex flex-wrap items-center gap-2 px-3 py-2 text-xs text-amber-800 dark:text-amber-200">
              {results.error}
              <button
                type="button"
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => setAttempt((value) => value + 1)}
                className="inline-flex items-center gap-1 font-semibold text-indigo-600 dark:text-indigo-400 hover:underline"
              >
                <RotateCw className="h-3 w-3" /> Повторить
              </button>
            </li>
          ) : options.length === 0 ? (
            <li className="px-3 py-2 text-xs text-slate-600 dark:text-slate-300">{emptyText}</li>
          ) : options.map((option, index) => (
            <li
              key={option.value}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={value?.value === option.value}
              // Keep focus in the input, so the click does not blur and close the list first.
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => pick(option)}
              onMouseEnter={() => setActive(index)}
              className={`cursor-pointer truncate px-3 py-2 ${
                index === active ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300' : 'text-slate-800 dark:text-slate-100'
              } ${value?.value === option.value ? 'font-semibold' : ''}`}
              title={option.label}
            >
              {option.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
