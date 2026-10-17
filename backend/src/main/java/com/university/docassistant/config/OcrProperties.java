package com.university.docassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * app.ocr: text recognition for scanned PDFs and images (US-07), with Tesseract through Tika. Runs only in the
 * background job, never in the upload request.
 *
 * @param enabled       off by default: needs Tesseract installed with the languages below
 * @param tesseractPath the folder of the tesseract executable (or the executable itself); empty = found on PATH
 * @param languages     Tesseract languages joined with "+"; default rus+kaz+eng
 * @param timeout       longest OCR of one document (also the limit for each Tesseract run); default 120 s
 * @param maxPages      only the first pages of a longer PDF are recognized; default 20
 */
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(Boolean enabled, String tesseractPath, String languages, Duration timeout, Integer maxPages) {

    public static final String DEFAULT_LANGUAGES = "rus+kaz+eng";

    public OcrProperties {
        enabled = enabled != null && enabled;
        tesseractPath = tesseractPath == null ? "" : tesseractPath.strip();
        languages = languages == null || languages.isBlank() ? DEFAULT_LANGUAGES : languages.strip();
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(120) : timeout;
        maxPages = maxPages == null || maxPages < 1 ? 20 : maxPages;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
