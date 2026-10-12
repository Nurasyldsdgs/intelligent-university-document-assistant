package com.university.docassistant.service;

import com.university.docassistant.dto.UploadDtos.SkippedFile;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipArchiveExtractorTest {

    private static final long LIMIT = 1024;

    private static byte[] zip(Map<String, byte[]> entries, Charset names) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, names)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static ZipArchiveExtractor.Result extract(byte[] zip) {
        return ZipArchiveExtractor.extract(zip, DocumentService::isAllowedDocument, LIMIT);
    }

    @Test
    void extractsSupportedFilesFromFoldersByTheirNameOnly() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("week1/syllabus.pdf", "pdf".getBytes());
        entries.put("lab.docx", "docx".getBytes());
        entries.put("scan.PNG", "png".getBytes());
        entries.put("notes.exe", "x".getBytes());
        entries.put("__MACOSX/._lab.docx", "junk".getBytes());

        ZipArchiveExtractor.Result result = extract(zip(entries, StandardCharsets.UTF_8));

        assertThat(result.files()).extracting(ZipArchiveExtractor.ExtractedFile::fileName)
                .containsExactly("syllabus.pdf", "lab.docx", "scan.PNG");
        assertThat(result.skipped()).containsExactly(new SkippedFile("notes.exe", "Неподдерживаемый формат файла"));
    }

    @Test
    void rejectsZipSlipEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../../etc/evil.pdf", "x".getBytes());
        entries.put("/abs/evil.pdf", "x".getBytes());
        entries.put("C:/Windows/evil.pdf", "x".getBytes());
        entries.put("ok/../../evil.pdf", "x".getBytes());
        entries.put("good.pdf", "x".getBytes());

        ZipArchiveExtractor.Result result = extract(zip(entries, StandardCharsets.UTF_8));

        assertThat(result.files()).extracting(ZipArchiveExtractor.ExtractedFile::fileName).containsExactly("good.pdf");
        assertThat(result.skipped()).hasSize(4).allMatch(skipped -> skipped.reason().equals("Небезопасный путь в архиве"));
    }

    @Test
    void skipsOversizedEmptyAndNestedArchives() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("big.pdf", new byte[(int) LIMIT + 1]);
        entries.put("empty.pdf", new byte[0]);
        entries.put("inner.zip", "PK".getBytes());

        ZipArchiveExtractor.Result result = extract(zip(entries, StandardCharsets.UTF_8));

        assertThat(result.files()).isEmpty();
        assertThat(result.skipped()).extracting(SkippedFile::fileName).containsExactly("big.pdf", "empty.pdf", "inner.zip");
    }

    @Test
    void readsCyrillicNamesFromWindowsArchives() throws IOException {
        byte[] zip = zip(Map.of("Силлабус.pdf", "x".getBytes()), Charset.forName("CP866"));

        assertThat(extract(zip).files()).extracting(ZipArchiveExtractor.ExtractedFile::fileName)
                .containsExactly("Силлабус.pdf");
    }

    @Test
    void notAZipIsRejected() {
        assertThatThrownBy(() -> extract("%PDF-1.7 not a zip".getBytes()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ZIP");
    }
}
