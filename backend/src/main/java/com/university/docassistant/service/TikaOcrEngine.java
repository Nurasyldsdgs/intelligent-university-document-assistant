package com.university.docassistant.service;

import com.university.docassistant.config.OcrProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OCR with Tesseract through Tika (US-07). The configured TesseractOCRParser replaces the one Tika would load by
 * itself; PDFs are parsed with the AUTO OCR strategy (pages without a text layer are recognized), cut to the first
 * app.ocr.max-pages pages first. Each document has app.ocr.timeout in total, which is also the limit of every single
 * Tesseract run. Availability (Tesseract and every configured language) is checked once, on first use.
 */
@Slf4j
@Component
public class TikaOcrEngine implements OcrEngine {

    private final OcrProperties properties;
    private final ExecutorService executor = Executors.newCachedThreadPool(new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "ocr-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });
    private volatile Parser parser; // Set when OCR is available.
    private volatile Boolean available;

    public TikaOcrEngine(OcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        Boolean known = available;
        return known != null ? known : check();
    }

    /** Looks for Tesseract and the configured languages once; logs one WARN listing what is missing. */
    synchronized boolean check() {
        if (available != null) {
            return available;
        }
        if (!properties.isEnabled()) {
            available = false;
            return false;
        }
        TesseractOCRParser tesseract = new TesseractOCRParser();
        boolean found;
        Set<String> installed;
        try {
            if (!properties.tesseractPath().isEmpty()) {
                tesseract.setTesseractPath(executableFolder(properties.tesseractPath()));
            }
            tesseract.initialize(Map.of());
            found = tesseract.hasTesseract();
            // Asked directly: Tika's own getLangs() comes back empty with Tesseract 5.5, whose list header names
            // the tessdata folder. Tika's answer stays as the fallback.
            installed = found ? listLanguages(executable(properties.tesseractPath()), tesseract.getLangs()) : Set.of();
        } catch (Exception ex) {
            log.debug("Tesseract check failed", ex);
            found = false;
            installed = Set.of();
        }
        List<String> missing = missingParts(found, installed, properties.languages());
        if (!missing.isEmpty()) {
            log.warn("OCR is enabled but cannot run, scanned documents stay without text. Missing: {}. "
                            + "Check OCR_TESSERACT_PATH (now \"{}\") and the Tesseract language packs.",
                    String.join("; ", missing), properties.tesseractPath());
            available = false;
            return false;
        }
        parser = new AutoDetectParser(new DefaultParser(MediaTypeRegistry.getDefaultRegistry(), new ServiceLoader(),
                List.of(TesseractOCRParser.class)), tesseract);
        log.info("OCR ready: Tesseract with {} (timeout {} s, at most {} PDF pages)", properties.languages(),
                properties.timeout().toSeconds(), properties.maxPages());
        available = true;
        return true;
    }

    /** {@code tesseract --list-langs}: the language codes after the header line; {@code fallback} if it cannot run. */
    static Set<String> listLanguages(String executable, Set<String> fallback) {
        try {
            Process process = new ProcessBuilder(executable, "--list-langs").redirectErrorStream(true).start();
            String output;
            try (InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return fallback == null ? Set.of() : fallback;
            }
            Set<String> languages = parseLanguageList(output);
            return languages.isEmpty() && fallback != null ? fallback : languages;
        } catch (IOException ex) {
            return fallback == null ? Set.of() : fallback;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallback == null ? Set.of() : fallback;
        }
    }

    /** The codes from "List of available languages in "...tessdata/" (4):
eng
kaz
osd
rus". */
    static Set<String> parseLanguageList(String output) {
        Set<String> languages = new java.util.TreeSet<>();
        for (String line : output.split("\\R")) {
            String code = line.strip();
            if (code.matches("[a-z][a-z_]+")) {
                languages.add(code);
            }
        }
        return languages;
    }

    /** The executable to run: in OCR_TESSERACT_PATH's folder, or "tesseract" from PATH. */
    static String executable(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return "tesseract";
        }
        String windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? ".exe" : "";
        return new File(executableFolder(configuredPath), "tesseract" + windows).getPath();
    }

    /** What keeps OCR from running: the program itself, or each configured language without its language pack. */
    static List<String> missingParts(boolean tesseractFound, Set<String> installedLanguages, String languages) {
        if (!tesseractFound) {
            return List.of("tesseract executable");
        }
        List<String> missing = new ArrayList<>();
        for (String language : languages.split("\\+")) {
            String code = language.strip().toLowerCase(Locale.ROOT);
            if (!code.isEmpty() && (installedLanguages == null || !installedLanguages.contains(code))) {
                missing.add("language pack '" + code + "'");
            }
        }
        return missing;
    }

    @Override
    public Recognized recognize(byte[] content, String fileName, String contentType) throws OcrException {
        if (!isAvailable()) {
            throw new OcrException("OCR is not available", false, null);
        }
        byte[] input = "application/pdf".equals(contentType) ? firstPages(content, properties.maxPages()) : content;
        Future<Recognized> job = executor.submit(() -> parse(input, fileName, contentType));
        try {
            return job.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            job.cancel(true); // A running Tesseract process stops at its own timeout, the same limit.
            throw new OcrException("OCR took longer than " + properties.timeout().toSeconds() + " s", true, ex);
        } catch (InterruptedException ex) {
            job.cancel(true);
            Thread.currentThread().interrupt();
            throw new OcrException("OCR was interrupted", false, ex);
        } catch (ExecutionException ex) {
            throw new OcrException("OCR failed: " + ex.getCause(), false, ex.getCause());
        }
    }

    private Recognized parse(byte[] content, String fileName, String contentType) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        metadata.set(HttpHeaders.CONTENT_TYPE, contentType);
        TesseractOCRConfig ocr = new TesseractOCRConfig();
        ocr.setLanguage(properties.languages());
        ocr.setTimeoutSeconds((int) Math.max(1, properties.timeout().toSeconds()));
        PDFParserConfig pdf = new PDFParserConfig();
        pdf.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.AUTO); // Only pages without (enough) text are recognized.
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        context.set(TesseractOCRConfig.class, ocr);
        context.set(PDFParserConfig.class, pdf);
        BodyContentHandler handler = new BodyContentHandler(DocumentParserService.MAX_TEXT_LENGTH);
        boolean truncated = false;
        try {
            parser.parse(new ByteArrayInputStream(content), handler, metadata, context);
        } catch (Exception ex) {
            if (!WriteLimitReachedException.isWriteLimitReached(ex)) {
                throw ex;
            }
            truncated = true;
        }
        return new Recognized(DocumentParserService.clean(handler.toString()), truncated);
    }

    /** The PDF cut to its first {@code maxPages} pages; unchanged when shorter or unreadable (Tika then decides). */
    static byte[] firstPages(byte[] pdf, int maxPages) {
        try (PDDocument document = PDDocument.load(pdf)) {
            int pages = document.getNumberOfPages();
            if (pages <= maxPages) {
                return pdf;
            }
            for (int index = pages - 1; index >= maxPages; index--) {
                document.removePage(index);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            return pdf;
        }
    }

    /** OCR_TESSERACT_PATH may name the executable itself; Tika wants its folder. */
    static String executableFolder(String path) {
        File file = new File(path);
        boolean executable = file.isFile() || Arrays.asList("tesseract", "tesseract.exe")
                .contains(file.getName().toLowerCase(Locale.ROOT));
        return executable && file.getParent() != null ? file.getParent() : path;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
