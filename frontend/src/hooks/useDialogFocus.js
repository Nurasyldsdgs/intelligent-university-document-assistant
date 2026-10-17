import { useEffect, useRef, useState } from 'react'

const FOCUSABLE = [
  'a[href]', 'button:not([disabled])', 'input:not([disabled]):not([type="hidden"])', 'select:not([disabled])',
  'textarea:not([disabled])', '[tabindex]:not([tabindex="-1"])',
].join(',')

// Open dialogs, innermost last: only that one reacts to Escape and Tab (a confirm over an edit dialog).
const openDialogs = []

const focusableIn = (root) => [...root.querySelectorAll(FOCUSABLE)].filter((el) => el.getClientRects().length > 0)

/**
 * Modal focus handling for the dialog element in {@code ref}: on open, focus moves into it (unless an autoFocus
 * element already took it); Tab and Shift+Tab cycle inside it; Escape calls {@code onClose} unless {@code busy}
 * (a request is running); on close, focus returns to the element that opened the dialog. With dialogs stacked, only
 * the innermost one handles the keys.
 */
export default function useDialogFocus(ref, { onClose, busy = false }) {
  // Read on the first render: an autoFocus child takes focus at commit, before any effect runs.
  const [opener] = useState(() => document.activeElement)
  const latest = useRef({ onClose, busy })
  useEffect(() => {
    latest.current = { onClose, busy }
  })

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return undefined
    if (!dialog.contains(document.activeElement)) (focusableIn(dialog)[0] ?? dialog).focus()
    openDialogs.push(dialog)

    const onKeyDown = (event) => {
      if (openDialogs[openDialogs.length - 1] !== dialog) return
      if (event.key === 'Escape') {
        event.preventDefault()
        if (!latest.current.busy) latest.current.onClose()
        return
      }
      if (event.key !== 'Tab') return
      const items = focusableIn(dialog)
      if (items.length === 0) {
        event.preventDefault()
        dialog.focus()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      if (!dialog.contains(document.activeElement)) {
        event.preventDefault()
        first.focus()
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      openDialogs.splice(openDialogs.lastIndexOf(dialog), 1)
      // Only when the dialog really left the page (StrictMode re-runs effects on a mounted dialog). The opener may be
      // gone too (e.g. the deleted row); then the browser keeps focus where it is.
      queueMicrotask(() => {
        if (!dialog.isConnected && opener instanceof HTMLElement && opener.isConnected) opener.focus()
      })
    }
  }, [ref, opener])
}
