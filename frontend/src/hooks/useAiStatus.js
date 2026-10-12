import { useEffect, useState } from 'react'
import api from '../api/axios'

// One request per page load: whether the AI is configured does not change while the app is open.
let statusRequest = null

/** true / false once known, null while loading or if the status could not be fetched. */
export default function useAiStatus() {
  const [available, setAvailable] = useState(null)

  useEffect(() => {
    let cancelled = false
    statusRequest ??= api.get('/ai/status').then(({ data }) => data.available).catch(() => {
      statusRequest = null // Retry on the next mount instead of caching a network error.
      return null
    })
    statusRequest.then((value) => !cancelled && setAvailable(value))
    return () => {
      cancelled = true
    }
  }, [])

  return available
}
