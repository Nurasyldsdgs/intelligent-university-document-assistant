import { useEffect, useState } from 'react'
import api from '../api/axios'
import { AUTO_ANALYZED_CATEGORIES } from '../constants/documents'

const FAST_POLL_MS = 2000
const SLOW_POLL_MS = 5000
const FAST_PHASE_MS = 30 * 1000
const CLASSIFY_TIMEOUT_MS = 3 * 60 * 1000
const DEADLINES_TIMEOUT_MS = 60 * 1000
const MAX_IDS_PER_REQUEST = 50 // DocumentService.MAX_STATUS_IDS
const IN_PROGRESS = new Set(['PENDING', 'PROCESSING'])
// US-07: OCR of a scan runs before (or instead of) the classification.
const OCR_IN_PROGRESS = new Set(['PENDING', 'RUNNING'])
const working = (status) => IN_PROGRESS.has(status.aiStatus) || OCR_IN_PROGRESS.has(status.ocrStatus)

/**
 * Phase of a document before any poll: 'classifying' while the AI has not decided (or its scan is still being
 * recognized), 'deadlines' for a
 * SYLLABUS/ASSIGNMENT whose deadline analysis is still to come (a manual category), 'finished' otherwise.
 */
export function initialPhase(document, watchDeadlines = true) {
  if (working(document)) return 'classifying'
  if (watchDeadlines && document.aiStatus === 'MANUAL' && watchesDeadlines(document.category, document.hasExtractedText)) {
    return 'deadlines'
  }
  return 'finished'
}

function watchesDeadlines(category, hasText) {
  return Boolean(hasText) && AUTO_ANALYZED_CATEGORIES.has(category)
}

function chunks(ids) {
  const result = []
  for (let i = 0; i < ids.length; i += MAX_IDS_PER_REQUEST) result.push(ids.slice(i, i + MAX_IDS_PER_REQUEST))
  return result
}

/**
 * Polls GET /documents/status?ids=... for the given documents (`documents` must be memoized by the caller): one
 * request per tick for up to 50 of them, every 2 s for the first 30 s and every 5 s after that.
 * - 'classifying' until the status is DONE, FAILED or MANUAL (at most 3 minutes, then `timedOut`);
 * - with `deadlines` (default), a SYLLABUS/ASSIGNMENT with text is then watched up to 60 s more, until
 *   assignmentsCount is above zero and the same on two polls in a row (the analysis runs right after classification);
 * - 'finished'. A document missing from the answer (deleted, or no longer visible) is finished too.
 * A failed poll is simply retried on the next tick. Stops when everything is finished, on unmount, or when
 * `documents` changes.
 *
 * @returns {{ [id: number]: { aiStatus, category, aiConfidence, assignmentsCount, phase, watchedDeadlines,
 *            timedOut, updatedAt } }} only for documents polled at least once; see initialPhase for the rest.
 *            updatedAt (ms) tells a fresh result from one left over by an earlier polling of the same id.
 */
export default function useDocumentStatus(documents, { deadlines = true } = {}) {
  const [statuses, setStatuses] = useState({})

  useEffect(() => {
    const startedAt = Date.now()
    const tracked = new Map(documents.map((document) => {
      const phase = initialPhase(document, deadlines)
      return [document.id, {
        phase,
        hasText: document.hasExtractedText,
        watchedDeadlines: phase === 'deadlines',
        deadlinesSince: phase === 'deadlines' ? startedAt : null,
        lastCount: null,
      }]
    }))
    if (![...tracked.values()].some((item) => item.phase !== 'finished')) return undefined

    let stopped = false
    let timer = null

    const tick = async () => {
      const active = [...tracked].filter(([, item]) => item.phase !== 'finished')
      if (active.length === 0 || stopped) return

      const answers = await Promise.allSettled(chunks(active.map(([id]) => id)).map((ids) =>
        api.get('/documents/status', { params: { ids: ids.join(',') } })))
      if (stopped) return

      // ok: whether every request of this tick succeeded; a failed one is retried next tick, nothing is concluded.
      const ok = answers.every((answer) => answer.status === 'fulfilled')
      const byId = new Map()
      answers.forEach((answer) => {
        if (answer.status === 'fulfilled') answer.value.data.forEach((item) => byId.set(item.id, item))
      })

      const now = Date.now()
      const updates = {}
      active.forEach(([id, item]) => {
        const data = byId.get(id)
        if (data) {
          if (item.phase === 'classifying' && !working(data)) {
            // A recognized scan has text now, whatever the upload found; a category chosen by hand also gets its
            // deadline analysis after the OCR.
            const hasText = item.hasText || data.textFromOcr
            if (deadlines && (data.aiStatus === 'DONE' || (data.aiStatus === 'MANUAL' && data.textFromOcr))
              && watchesDeadlines(data.category, hasText)) {
              item.phase = 'deadlines'
              item.watchedDeadlines = true
              item.deadlinesSince = now
            } else {
              item.phase = 'finished'
            }
          } else if (item.phase === 'deadlines') {
            if (data.assignmentsCount > 0 && data.assignmentsCount === item.lastCount) item.phase = 'finished'
            item.lastCount = data.assignmentsCount
          }
        } else if (ok) {
          item.phase = 'finished' // Deleted, or no longer visible to this user: nothing more to wait for.
        }
        let timedOut = false
        if (item.phase === 'classifying' && now - startedAt > CLASSIFY_TIMEOUT_MS) {
          item.phase = 'finished'
          timedOut = true
        } else if (item.phase === 'deadlines' && now - item.deadlinesSince > DEADLINES_TIMEOUT_MS) {
          item.phase = 'finished'
        }
        updates[id] = { ...(data ?? {}), phase: item.phase, watchedDeadlines: item.watchedDeadlines, timedOut, updatedAt: now }
      })
      setStatuses((current) => {
        const next = { ...current }
        for (const [id, update] of Object.entries(updates)) next[id] = { ...current[id], ...update }
        return next
      })
      schedule()
    }

    function schedule() {
      if (stopped || ![...tracked.values()].some((item) => item.phase !== 'finished')) return
      timer = setTimeout(tick, Date.now() - startedAt < FAST_PHASE_MS ? FAST_POLL_MS : SLOW_POLL_MS)
    }

    schedule()
    return () => {
      stopped = true
      clearTimeout(timer)
    }
  }, [documents, deadlines])

  return statuses
}
