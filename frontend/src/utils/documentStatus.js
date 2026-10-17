// Live AI status for document rows (lists, document page), polled with useDocumentStatus.

/** US-07: the scan's text is still being recognized. */
export const isOcrRunning = (doc) => doc?.ocrStatus === 'PENDING' || doc?.ocrStatus === 'RUNNING'

/** The AI is classifying the document, or its scan is still being recognized: lists poll it until done. */
export const isClassifying = (doc) => doc?.aiStatus === 'PENDING' || doc?.aiStatus === 'PROCESSING' || isOcrRunning(doc)

/**
 * The row with what the last poll said about it. A row marked by a reclassify carries `pendingSince` (ms): polls from
 * before that moment are ignored, so an older "DONE" cannot override the fresh "PENDING".
 */
export function withLiveStatus(doc, status) {
  if (!status?.aiStatus) return doc
  if (doc.pendingSince && !(status.updatedAt >= doc.pendingSince)) return doc
  const merged = {
    ...doc,
    aiStatus: status.aiStatus,
    category: status.category ?? doc.category,
    aiConfidence: status.aiConfidence ?? null,
  }
  if (status.assignmentsCount !== undefined) merged.assignmentsCount = status.assignmentsCount
  if (status.ocrStatus !== undefined) merged.ocrStatus = status.ocrStatus
  if (status.textFromOcr !== undefined) merged.textFromOcr = status.textFromOcr
  // Mirrors the backend rule: exam answers are always hidden from students.
  if (merged.category === 'EXAM_ANSWERS') merged.visibility = 'TEACHER_ONLY'
  return merged
}
