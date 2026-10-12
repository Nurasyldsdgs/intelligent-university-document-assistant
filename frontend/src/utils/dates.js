// The one place that formats dates for the UI (ru-RU).
//
// The backend sends LocalDateTime without an offset ("2026-10-12T18:00:00"): that is already the wall-clock time of the
// app's time zone (the server's), so it is shown as is, never shifted by the viewer's browser zone. A value that does
// carry a zone (an ISO string with "Z"/"+05:00", a Date, a timestamp) is converted to VITE_APP_TIME_ZONE, or to the
// browser's zone when that is not set.

const APP_TIME_ZONE = import.meta.env.VITE_APP_TIME_ZONE || undefined

const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?)?$/

const options = {
  dateTime: { dateStyle: 'medium', timeStyle: 'short' },
  date: { dateStyle: 'medium' },
}
const formatters = new Map()

function formatter(kind, timeZone) {
  const key = `${kind}|${timeZone ?? ''}`
  if (!formatters.has(key)) formatters.set(key, new Intl.DateTimeFormat('ru-RU', { ...options[kind], timeZone }))
  return formatters.get(key)
}

function format(value, kind) {
  if (value === null || value === undefined || value === '') return '—'
  const local = typeof value === 'string' ? LOCAL_DATE_TIME.exec(value) : null
  if (local) {
    // Wall-clock fields formatted in UTC come out unchanged, whatever zone the browser is in.
    const [, y, mo, d, h = 0, mi = 0, s = 0] = local
    return formatter(kind, 'UTC').format(new Date(Date.UTC(+y, +mo - 1, +d, +h, +mi, +s)))
  }
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : formatter(kind, APP_TIME_ZONE).format(date)
}

/** "12 окт. 2026 г., 18:00"; "—" for an empty value. */
export const formatDateTime = (value) => format(value, 'dateTime')

/** "12 окт. 2026 г."; "—" for an empty value. */
export const formatDate = (value) => format(value, 'date')
