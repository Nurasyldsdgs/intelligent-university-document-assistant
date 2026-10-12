// Live AI status for document rows (lists, document page), polled with useDocumentStatus.

export const isClassifying = (doc) => doc?.aiStatus === 'PENDING' || doc?.aiStatus === 'PROCESSING'

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
  // Mirrors the backend rule: exam answers are always hidden from students.
  if (merged.category === 'EXAM_ANSWERS') merged.visibility = 'TEACHER_ONLY'
  return merged
}
