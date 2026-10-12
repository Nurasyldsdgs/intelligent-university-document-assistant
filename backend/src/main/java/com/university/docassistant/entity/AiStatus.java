package com.university.docassistant.entity;

/**
 * Where a document is in the background AI classification (DocumentProcessingService).
 * Students never see a document while it is PENDING or PROCESSING: it may still turn out to be EXAM_ANSWERS.
 */
public enum AiStatus {
    /** Uploaded without a category; waiting for the AI queue. */
    PENDING,
    /** The AI is classifying it right now. */
    PROCESSING,
    /** Classified by the AI; also every document from before this column existed. */
    DONE,
    /** The AI was unavailable or gave no usable answer; the category is OTHER until reclassified. */
    FAILED,
    /** The uploader chose the category; the AI did not classify it. */
    MANUAL
}
