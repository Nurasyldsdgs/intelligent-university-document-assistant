package com.university.docassistant.service;

import com.university.docassistant.dto.UploadDtos.SkippedFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Reads the documents out of a ZIP archive in memory (US-06). Safe by construction against Zip Slip: entry names are
 * never used as paths - only their last segment becomes the document's display name, and the content is stored
 * under a random name - and entries with absolute or ".." paths are rejected outright. Zip bombs are bounded by
 * {@link #MAX_ENTRIES}, the per-file limit and {@link #MAX_TOTAL_BYTES} of actually decompressed bytes.
 */
public final class ZipArchiveExtractor {

    static final int MAX_ENTRIES = 200;
    static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;
    /** Windows' built-in "Send to ZIP" writes non-ASCII names in the OEM code page, not UTF-8. */
    private static final Charset LEGACY_NAMES = Charset.forName("CP866");

    /** A file taken from the archive (or from a multipart request) that is ready to become a document. */
    public record ExtractedFile(String fileName, byte[] content) {
    }

    public record Result(List<ExtractedFile> files, List<SkippedFile> skipped) {
    }

    private ZipArchiveExtractor() {
    }

    /**
     * @param isAllowed        whether a file name has an accepted extension
     * @param maxDocumentBytes larger files are skipped (and decompression stops reading them at that size)
     * @throws IllegalStateException when the bytes are not a readable ZIP archive (message is shown to the user)
     */
    public static Result extract(byte[] zip, Predicate<String> isAllowed, long maxDocumentBytes) {
        if (!looksLikeZip(zip)) {
            throw new IllegalStateException("Файл не является корректным ZIP-архивом");
        }
        try {
            return read(zip, StandardCharsets.UTF_8, isAllowed, maxDocumentBytes);
        } catch (IllegalArgumentException ex) {
            // Names that are not valid UTF-8: an archive made by Windows Explorer.
            return read(zip, LEGACY_NAMES, isAllowed, maxDocumentBytes);
        }
    }

    private static Result read(byte[] zip, Charset names, Predicate<String> isAllowed,
                               long maxDocumentBytes) {
        List<ExtractedFile> files = new ArrayList<>();
        List<SkippedFile> skipped = new ArrayList<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), names)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName().replace('\\', '/');
                if (++entries > MAX_ENTRIES) {
                    skipped.add(new SkippedFile(path, "В архиве больше " + MAX_ENTRIES + " файлов, остальные пропущены"));
                    break;
                }
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                if (isSystemFile(path, fileName)) {
                    continue;
                }
                if (isUnsafe(path)) {
                    skipped.add(new SkippedFile(path, "Небезопасный путь в архиве"));
                    continue;
                }
                if (!isAllowed.test(fileName)) {
                    skipped.add(new SkippedFile(fileName, fileName.toLowerCase(Locale.ROOT).endsWith(".zip")
                            ? "Вложенные архивы не распаковываются" : "Неподдерживаемый формат файла"));
                    continue;
                }
                byte[] content = readLimited(in, maxDocumentBytes);
                if (content == null) {
                    skipped.add(new SkippedFile(fileName, "Файл больше " + maxDocumentBytes / (1024 * 1024) + " МБ"));
                    continue;
                }
                if (content.length == 0) {
                    skipped.add(new SkippedFile(fileName, "Пустой файл"));
                    continue;
                }
                total += content.length;
                if (total > MAX_TOTAL_BYTES) {
                    skipped.add(new SkippedFile(fileName, "Архив распаковывается больше чем в "
                            + MAX_TOTAL_BYTES / (1024 * 1024) + " МБ, остальные файлы пропущены"));
                    break;
                }
                files.add(new ExtractedFile(fileName, content));
            }
        } catch (ZipException ex) {
            throw new IllegalStateException("Файл не является корректным ZIP-архивом", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось прочитать ZIP-архив", ex);
        }
        return new Result(files, skipped);
    }

    /** Absolute paths, drive letters and ".." segments: the classic Zip Slip entries. */
    static boolean isUnsafe(String path) {
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            return true;
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSystemFile(String path, String fileName) {
        return path.startsWith("__MACOSX/") || fileName.startsWith(".") || fileName.equalsIgnoreCase("Thumbs.db")
                || fileName.isEmpty();
    }

    /** The entry's bytes, or null once more than {@code limit} bytes come out of it. */
    private static byte[] readLimited(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long read = 0;
        int n;
        while ((n = in.read(buffer)) != -1) {
            read += n;
            if (read > limit) {
                return null;
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    // Every ZIP starts with a "PK" signature; ZipInputStream would silently read anything else as an empty archive.
    private static boolean looksLikeZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';
    }
}
