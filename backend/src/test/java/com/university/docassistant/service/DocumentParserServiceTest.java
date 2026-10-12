package com.university.docassistant.service;

import com.university.docassistant.service.DocumentParserService.ExtractedText;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserServiceTest {

    private final DocumentParserService parser = new DocumentParserService();

    @Test
    void extractsTextFromPdf() throws IOException {
        ExtractedText result = extract(pdf("Syllabus CS101: deadlines and grading", null), "s.pdf", "application/pdf");

        assertThat(result.text()).contains("Syllabus CS101: deadlines and grading");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void extractsCyrillicTextFromDocx() throws IOException {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Лекция 1. Введение в алгоритмы");
            document.write(out);
            docx = out.toByteArray();
        }

        ExtractedText result = extract(docx, "lecture.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThat(result.text()).isEqualTo("Лекция 1. Введение в алгоритмы");
    }

    @Test
    void passwordProtectedPdfYieldsNoText() throws IOException {
        ExtractedText result = extract(pdf("secret", "user-password"), "locked.pdf", "application/pdf");

        assertThat(result).isEqualTo(ExtractedText.NONE);
    }

    @Test
    void corruptedFileYieldsNoText() {
        byte[] garbage = "%PDF-1.7\n this is not really a pdf \u0001\u0002".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(extract(garbage, "broken.pdf", "application/pdf")).isEqualTo(ExtractedText.NONE);
    }

    @Test
    void removesNulAndControlCharacters() {
        byte[] text = "before\u0000after\u0007 end\n\n\n\n\nnext".getBytes(StandardCharsets.UTF_8);

        ExtractedText result = extract(text, "notes.txt", "text/plain");

        assertThat(result.text()).isEqualTo("beforeafter end\n\nnext");
    }

    @Test
    void truncatesLongTextAtTheLimit() {
        byte[] text = "word ".repeat(30_000).getBytes(StandardCharsets.UTF_8); // 150 000 characters

        ExtractedText result = extract(text, "long.txt", "text/plain");

        assertThat(result.truncated()).isTrue();
        assertThat(result.text().length()).isLessThanOrEqualTo(DocumentParserService.MAX_TEXT_LENGTH);
        assertThat(result.text()).startsWith("word word");
    }

    @Test
    void blankFileYieldsNoText() {
        assertThat(extract(" \n\t ".getBytes(StandardCharsets.UTF_8), "empty.txt", "text/plain"))
                .isEqualTo(ExtractedText.NONE);
    }

    @Test
    void snippetIsSingleLineAndBounded() {
        assertThat(DocumentParserService.snippet("a\n\nb\tc")).isEqualTo("a b c");
        assertThat(DocumentParserService.snippet("x".repeat(500))).hasSize(DocumentParserService.SNIPPET_LENGTH);
        assertThat(DocumentParserService.snippet(null)).isNull();
    }

    private ExtractedText extract(byte[] content, String fileName, String contentType) {
        return parser.extractText(new ByteArrayInputStream(content), fileName, contentType);
    }

    private static byte[] pdf(String text, String userPassword) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            if (userPassword != null) {
                StandardProtectionPolicy policy =
                        new StandardProtectionPolicy("owner-password", userPassword, new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
