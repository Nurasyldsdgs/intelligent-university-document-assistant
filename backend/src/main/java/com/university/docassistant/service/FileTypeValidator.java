package com.university.docassistant.service;

import com.university.docassistant.exception.ErrorCodes;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Checks that an upload really is what its extension says, by detecting the type from the bytes with Apache Tika.
 * A PDF-named text file, a plain ZIP named .docx or an empty file are refused. The detected type, not the one the
 * browser declared, is what gets stored as the document's content type. Stateless and thread-safe.
 */
public final class FileTypeValidator {

    static final String MISMATCH = "Содержимое файла не соответствует расширению";
    static final String UNSUPPORTED = "Неподдерживаемый формат файла";
    static final String EMPTY = "Пустой файл";

    private static final MediaType DOCX = MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final MediaType PPTX = MediaType.application("vnd.openxmlformats-officedocument.presentationml.presentation");

    /** Detected base types accepted for each extension of DocumentService.ALLOWED_TYPES. */
    private static final Map<String, Predicate<MediaType>> ACCEPTED = Map.of(
            "pdf", type -> type.equals(MediaType.application("pdf")),
            // OOXML files are ZIP containers: the specific OOXML type is required, application/zip is not enough.
            "docx", DOCX::equals,
            "pptx", PPTX::equals,
            // OLE2 compound files; Tika names the generic container x-tika-msoffice when it cannot tell more.
            "doc", Set.of(MediaType.application("msword"), MediaType.application("x-tika-msoffice"))::contains,
            "txt", type -> type.getType().equals("text"),
            "png", type -> type.equals(MediaType.image("png")),
            "jpg", type -> type.equals(MediaType.image("jpeg")),
            "jpeg", type -> type.equals(MediaType.image("jpeg")));

    private static final Detector DETECTOR = TikaConfig.getDefaultConfig().getDetector();

    /** {@code detectedType} on success, otherwise {@code problem} (a Russian reason shown to the user). */
    public record Result(String detectedType, String problem) {

        public boolean isValid() {
            return problem == null;
        }

        /** The ErrorCodes code of the problem; null when valid. */
        public String code() {
            if (problem == null) {
                return null;
            }
            return switch (problem) {
                case MISMATCH -> ErrorCodes.UPLOAD_CONTENT_MISMATCH;
                case EMPTY -> ErrorCodes.UPLOAD_EMPTY;
                default -> ErrorCodes.UPLOAD_UNSUPPORTED_TYPE;
            };
        }

        static Result ok(MediaType type) {
            return new Result(type.toString(), null);
        }

        static Result fail(String problem) {
            return new Result(null, problem);
        }
    }

    private FileTypeValidator() {
    }

    /** @param fileName the sanitized file name; its extension decides which content is acceptable */
    public static Result validate(byte[] content, String fileName) {
        Predicate<MediaType> accepted = ACCEPTED.get(extensionOf(fileName));
        if (accepted == null) {
            return Result.fail(UNSUPPORTED);
        }
        if (content == null || content.length == 0) {
            // Checked here: with no bytes to look at, Tika would fall back to the name and "detect" the extension.
            return Result.fail(EMPTY);
        }
        MediaType detected = detect(content);
        return detected != null && accepted.test(detected) ? Result.ok(detected) : Result.fail(MISMATCH);
    }

    /**
     * The base type (no parameters) detected from the bytes alone, or null when detection failed.
     * The file name is deliberately not passed as RESOURCE_NAME_KEY: Tika lets a name refine the content's type when
     * the named type is a subtype of it, so a plain ZIP named "x.docx" would be reported as DOCX and an OLE2 file
     * named "x.doc" as Word - exactly the mismatches this check exists to catch.
     */
    static MediaType detect(byte[] content) {
        Metadata metadata = new Metadata();
        // TikaInputStream lets the container detectors open ZIP and OLE2 files to see what is inside.
        try (TikaInputStream stream = TikaInputStream.get(content)) {
            return DETECTOR.detect(stream, metadata).getBaseType();
        } catch (IOException ex) {
            return null;
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
