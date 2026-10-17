package com.university.docassistant.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * Extracts plain text from uploaded files (PDF, DOCX, DOC, TXT, RTF, ODT, ...) with Apache Tika.
 * Never throws: an encrypted, corrupted or unsupported file yields {@link ExtractedText#NONE}, so a failed
 * extraction never fails the upload itself.
 */
@Slf4j
@Service
public class DocumentParserService {

    /** Upper bound on the stored text; Tika stops reading once it is reached, so huge files stay cheap. */
    public static final int MAX_TEXT_LENGTH = 100_000;
    public static final int SNIPPET_LENGTH = 200;

    // PostgreSQL rejects NUL in text columns ("invalid byte sequence for encoding UTF8: 0x00");
    // the other control characters and U+FFFD (what a decoder emits for undecodable bytes) are noise from
    // binary formats. Tab, LF and CR are kept.
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\uFFFD]");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \\t]+(?=\\r?\\n)");
    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("(\\r?\\n){3,}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** {@code text} is null when the file has no readable text; {@code truncated} - the length limit was hit. */
    public record ExtractedText(String text, boolean truncated) {
        public static final ExtractedText NONE = new ExtractedText(null, false);
    }

    // Thread-safe; type detection and the parser registry are set up once.
    private final Parser parser = new AutoDetectParser();

    /**
     * @param fileName    original name, used as a type-detection hint
     * @param contentType type sent by the client, used as a hint only: Tika checks the actual bytes
     */
    public ExtractedText extractText(InputStream stream, String fileName, String contentType) {
        Metadata metadata = new Metadata();
        if (fileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        if (contentType != null) {
            metadata.set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser); // Also extract the text of embedded files (e.g. attachments in a DOCX).
        // Never OCR here, in the upload request: Tika would start Tesseract by itself if it is on PATH. Scans are
        // recognized later by the background job (DocumentOcrService, US-07).
        TesseractOCRConfig noOcr = new TesseractOCRConfig();
        noOcr.setSkipOcr(true);
        context.set(TesseractOCRConfig.class, noOcr);
        PDFParserConfig pdf = new PDFParserConfig();
        pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        context.set(PDFParserConfig.class, pdf);
        BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);

        boolean truncated = false;
        try {
            parser.parse(stream, handler, metadata, context);
        } catch (EncryptedDocumentException ex) {
            log.warn("Skipping text extraction of password-protected file '{}'", fileName);
            return ExtractedText.NONE;
        } catch (Exception ex) {
            if (!WriteLimitReachedException.isWriteLimitReached(ex)) {
                log.warn("Could not extract text from '{}' ({}): {}", fileName, contentType, ex.toString());
                return ExtractedText.NONE;
            }
            truncated = true; // The handler keeps the text up to the limit.
        } catch (LinkageError | StackOverflowError ex) {
            // Corrupted or exotic files can fail anywhere inside a parser, including with errors.
            log.warn("Could not extract text from '{}' ({}): {}", fileName, contentType, ex.toString());
            return ExtractedText.NONE;
        }

        String text = clean(handler.toString());
        return text == null ? ExtractedText.NONE : new ExtractedText(text, truncated);
    }

    /** First {@link #SNIPPET_LENGTH} characters of the text on a single line, or {@code null}. */
    public static String snippet(String text) {
        if (text == null) {
            return null;
        }
        String oneLine = WHITESPACE.matcher(text).replaceAll(" ").trim();
        if (oneLine.isEmpty()) {
            return null;
        }
        if (oneLine.length() <= SNIPPET_LENGTH) {
            return oneLine;
        }
        // Never cuts a surrogate pair in half: a lone surrogate is not valid UTF-8.
        int end = Character.isHighSurrogate(oneLine.charAt(SNIPPET_LENGTH - 1)) ? SNIPPET_LENGTH - 1 : SNIPPET_LENGTH;
        return oneLine.substring(0, end);
    }

    static String clean(String text) {
        String cleaned = CONTROL_CHARS.matcher(text).replaceAll("");
        cleaned = TRAILING_SPACES.matcher(cleaned).replaceAll("");
        cleaned = EXTRA_BLANK_LINES.matcher(cleaned).replaceAll("\n\n").strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
