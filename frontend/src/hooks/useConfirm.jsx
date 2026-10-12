import { useCallback, useState } from 'react'
import ConfirmDialog from '../components/ConfirmDialog'

/**
 * An awaitable ConfirmDialog, a drop-in for window.confirm:
 *   const [confirm, confirmDialog] = useConfirm()
 *   if (!(await confirm({ title, message, confirmLabel }))) return
 * and render {confirmDialog} once in the component.
 */
export default function useConfirm() {
  const [request, setRequest] = useState(null) // { title, message, confirmLabel, tone, resolve }

  const confirm = useCallback((options) => new Promise((resolve) => setRequest({ ...options, resolve })), [])

  const answer = (value) => {
    request?.resolve(value)
    setRequest(null)
  }

  const dialog = request && (
    <ConfirmDialog
      title={request.title}
      confirmLabel={request.confirmLabel}
      tone={request.tone}
      onConfirm={() => answer(true)}
      onCancel={() => answer(false)}
    >
      {request.message}
    </ConfirmDialog>
  )

  return [confirm, dialog]
}
