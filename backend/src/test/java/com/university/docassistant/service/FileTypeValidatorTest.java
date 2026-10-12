package com.university.docassistant.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Real files pass; files whose bytes do not match their extension are refused. */
class FileTypeValidatorTest {

    @Test
    void realPdfDocxPptxPngJpegAndTextPass() throws IOException {
        assertValid(pdf(), "lecture.pdf", "application/pdf");
        assertValid(docx(), "syllabus.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertValid(pptx(), "slides.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        assertValid(image("png"), "scan.png", "image/png");
        assertValid(image("jpg"), "scan.jpg", "image/jpeg");
        assertValid(image("jpg"), "SCAN.JPEG", "image/jpeg");
        assertValid("Срок сдачи: 15 ноября".getBytes(StandardCharsets.UTF_8), "notes.txt", "text/plain");
    }

    @Test
    void textRenamedToPdfFails() {
        assertProblem("Это не PDF, а просто текст".getBytes(StandardCharsets.UTF_8), "fake.pdf",
                FileTypeValidator.MISMATCH);
    }

    @Test
    void plainZipRenamedToDocxFails() throws IOException {
        assertProblem(zip(), "fake.docx", FileTypeValidator.MISMATCH);
    }

    @Test
    void docxRenamedToPptxAndPngRenamedToJpgFail() throws IOException {
        assertProblem(docx(), "fake.pptx", FileTypeValidator.MISMATCH);
        assertProblem(image("png"), "fake.jpg", FileTypeValidator.MISMATCH);
        assertProblem(pdf(), "fake.txt", FileTypeValidator.MISMATCH);
    }

    @Test
    void emptyFileFails() {
        assertProblem(new byte[0], "empty.pdf", FileTypeValidator.EMPTY);
    }

    @Test
    void unknownExtensionFails() {
        assertProblem("MZ...".getBytes(StandardCharsets.US_ASCII), "virus.exe", FileTypeValidator.UNSUPPORTED);
    }

    @Test
    void oldPowerPointIsNotAcceptedAsWord() throws IOException {
        // An OLE2 file that Tika can identify as something other than Word must not pass as .doc.
        assertProblem(ppt(), "fake.doc", FileTypeValidator.MISMATCH);
    }

    private static void assertValid(byte[] content, String fileName, String expectedType) {
        FileTypeValidator.Result result = FileTypeValidator.validate(content, fileName);
        assertThat(result.problem()).as(fileName).isNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.detectedType()).isEqualTo(expectedType);
    }

    private static void assertProblem(byte[] content, String fileName, String problem) {
        FileTypeValidator.Result result = FileTypeValidator.validate(content, fileName);
        assertThat(result.isValid()).as(fileName + " detected as " + result.detectedType()).isFalse();
        assertThat(result.problem()).isEqualTo(problem);
        assertThat(result.detectedType()).isNull();
    }

    private static byte[] pdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] docx() throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Силлабус курса");
            document.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] pptx() throws IOException {
        try (XMLSlideShow slides = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            slides.createSlide();
            slides.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] ppt() throws IOException {
        try (HSLFSlideShow slides = new HSLFSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            slides.createSlide();
            slides.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] image(String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private static byte[] zip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry("readme.txt"));
            out.write("hello".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return bytes.toByteArray();
    }
}
