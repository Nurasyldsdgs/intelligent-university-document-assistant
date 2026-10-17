package com.university.docassistant.service;

/**
 * Recognizes the text of a scanned PDF or an image. The production engine is {@link TikaOcrEngine} (Tesseract);
 * tests stub this interface.
 */
public interface OcrEngine {

    /** Whether OCR is switched on and Tesseract with every configured language was found. */
    boolean isAvailable();

    /**
     * The recognized text (null when nothing was found), cleaned like extracted text and capped at
     * DocumentParserService.MAX_TEXT_LENGTH.
     *
     * @throws OcrException on a timeout or a failure of the recognizer
     */
    Recognized recognize(byte[] content, String fileName, String contentType) throws OcrException;

    record Recognized(String text, boolean truncated) {
    }

    class OcrException extends Exception {

        private final boolean timeout;

        public OcrException(String message, boolean timeout, Throwable cause) {
            super(message, cause);
            this.timeout = timeout;
        }

        public boolean isTimeout() {
            return timeout;
        }
    }
}
