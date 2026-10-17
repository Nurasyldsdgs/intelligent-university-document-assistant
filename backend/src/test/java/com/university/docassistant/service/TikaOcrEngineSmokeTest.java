package com.university.docassistant.service;

import com.university.docassistant.config.OcrProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real Tesseract through Tika on a PDF that has no text layer, only an image of text. Opt-in, so the normal build
 * needs no Tesseract:
 * <pre>
 * mvnw test -Dtest=TikaOcrEngineSmokeTest "-Docr.tesseract.path=C:/Program Files/Tesseract-OCR"
 * </pre>
 */
@EnabledIfSystemProperty(named = "ocr.tesseract.path", matches = ".+")
class TikaOcrEngineSmokeTest {

    @Test
    void recognizesRussianTextInAScannedPdf() throws Exception {
        TikaOcrEngine engine = new TikaOcrEngine(new OcrProperties(true, System.getProperty("ocr.tesseract.path"),
                "rus+kaz+eng", Duration.ofSeconds(120), 20));
        assertThat(engine.isAvailable()).as("tesseract with rus, kaz, eng").isTrue();

        OcrEngine.Recognized recognized = engine.recognize(scannedPdf("Лабораторная работа 1", "Сдать до 20.10.2026"),
                "scan.pdf", "application/pdf");

        assertThat(recognized.text()).isNotNull();
        String text = recognized.text().toLowerCase();
        assertThat(text).contains("лабораторная", "работа", "20.10.2026");
        engine.shutdown();
    }

    /** One A4 page holding only a picture of the lines: what a scanner produces. */
    private static byte[] scannedPdf(String... lines) throws Exception {
        BufferedImage image = new BufferedImage(1654, 600, BufferedImage.TYPE_INT_RGB); // ~200 dpi width of A4
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Arial", Font.PLAIN, 56));
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(lines[i], 100, 160 + i * 120);
        }
        graphics.dispose();
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject picture = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float width = PDRectangle.A4.getWidth();
                content.drawImage(picture, 0, PDRectangle.A4.getHeight() - width * image.getHeight() / image.getWidth(),
                        width, width * image.getHeight() / image.getWidth());
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
