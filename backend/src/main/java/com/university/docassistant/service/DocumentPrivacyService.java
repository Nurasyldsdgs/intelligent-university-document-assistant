package com.university.docassistant.service;

import com.university.docassistant.config.PrivacyProperties;
import com.university.docassistant.util.PersonalDataMasker;
import com.university.docassistant.util.PersonalDataMasker.Exemptions;
import com.university.docassistant.util.PersonalDataMasker.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;

/**
 * US-09: the masked copy of a document's text - what goes to the external AI, into Q&A chunks, the snippet and
 * full-text search. The original stays in documents.extracted_text. With app.privacy.enabled=false the copy is the
 * text unchanged, so every consumer still reads the same column.
 */
@Service
@RequiredArgsConstructor
public class DocumentPrivacyService {

    private final PrivacyProperties properties;

    /**
     * Masks a document's text. The course teacher's own e-mail stays readable (a syllabus names its teacher); the
     * profile has no phone yet, so there is no phone to keep.
     */
    public Result mask(String text, String teacherEmail) {
        if (!properties.isEnabled()) {
            return new Result(text, 0, Set.of());
        }
        Set<String> emails = new HashSet<>();
        if (teacherEmail != null) {
            emails.add(teacherEmail);
        }
        return PersonalDataMasker.mask(text, new Exemptions(Set.copyOf(properties.publicEmailDomains()), emails, Set.of()));
    }

    /**
     * Everything stored from a document's text besides the original: the masked copy with its counts, the snippet
     * cut from it, and the fingerprint of the masking settings. Used for uploaded and for OCR-recognized text alike.
     */
    public PreparedText prepare(String text, String teacherEmail) {
        Result masked = mask(text, teacherEmail);
        return new PreparedText(masked, DocumentParserService.snippet(masked.text()), fingerprint());
    }

    public record PreparedText(Result masked, String snippet, String fingerprint) {
    }

    /** A file name goes to the AI with the text when classifying ("Иванов_ИИН_990101300013.pdf" happens). */
    public String maskFileName(String fileName) {
        if (!properties.isEnabled() || fileName == null) {
            return fileName;
        }
        return PersonalDataMasker.mask(fileName,
                new Exemptions(Set.copyOf(properties.publicEmailDomains()), Set.of(), Set.of())).text();
    }

    /**
     * What a stored masked text was made with: the masker version and the settings. documents.pii_scan keeps it;
     * rows with another value are masked again at startup (DocumentPrivacyBackfill), so turning privacy on or
     * changing the public domains takes effect for old documents too.
     */
    public String fingerprint() {
        String settings = "v" + PersonalDataMasker.VERSION + ";" + (properties.isEnabled() ? "on" : "off") + ";"
                + String.join(",", properties.publicEmailDomains());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(settings.getBytes(StandardCharsets.UTF_8));
            return "v" + PersonalDataMasker.VERSION + "-" + HexFormat.of().formatHex(hash, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }
}
