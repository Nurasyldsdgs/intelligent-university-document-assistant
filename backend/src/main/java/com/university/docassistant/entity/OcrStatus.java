package com.university.docassistant.entity;

/**
 * Text recognition of a scanned document (US-07); documents.ocr_status is null when none is needed.
 * PENDING: queued; RUNNING: in progress (back to PENDING after a restart); DONE: the text came from OCR;
 * FAILED: OCR failed, timed out or found no text - the teacher sees "Нужна проверка".
 */
public enum OcrStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}
