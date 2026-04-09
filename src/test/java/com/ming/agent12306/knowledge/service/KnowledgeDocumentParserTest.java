package com.ming.agent12306.knowledge.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentParserTest {

    @Test
    void shouldExtractChineseTextFromImageOnlyPdfWhenTesseractIsAvailable() throws Exception {
        Assumptions.assumeTrue(isTesseractAvailable(), "tesseract is required for OCR test");

        KnowledgeDocumentParser parser = new KnowledgeDocumentParser();

        String text = parser.parse(createImageOnlyPdf());

        String normalized = text.replaceAll("\\s+", "");
        assertTrue(normalized.contains("退票"), "expected OCR result to contain Chinese ticket refund text");
    }

    @Test
    void shouldExtractChineseTextFromPngWhenTesseractIsAvailable() throws Exception {
        Assumptions.assumeTrue(isTesseractAvailable(), "tesseract is required for OCR test");

        BodyContentHandler handler = new BodyContentHandler(-1);
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setLanguage("chi_sim+eng");
        context.set(TesseractOCRConfig.class, config);

        parser.parse(createImageOnlyPng(), handler, new Metadata(), context);

        String normalized = handler.toString().replaceAll("\\s+", "");
        assertTrue(normalized.contains("退票"), "expected direct image OCR result to contain Chinese ticket refund text");
    }

    private boolean isTesseractAvailable() {
        try {
            Process process = new ProcessBuilder("tesseract", "--list-langs")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return exitCode == 0 && output.contains("chi_sim");
        } catch (IOException | InterruptedException ex) {
            return false;
        }
    }

    private byte[] createImageOnlyPdf() throws IOException {
        System.setProperty("java.awt.headless", "true");

        BufferedImage image = createSourceImage();
        ByteArrayOutputStream imageOutput = new ByteArrayOutputStream();
        ImageIO.write(image, "png", imageOutput);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream pdfOutput = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
            document.addPage(page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(pdImage, 0, 0, image.getWidth(), image.getHeight());
            }
            document.save(pdfOutput);
            return pdfOutput.toByteArray();
        }
    }

    private java.io.InputStream createImageOnlyPng() throws IOException, SAXException {
        ByteArrayOutputStream imageOutput = new ByteArrayOutputStream();
        ImageIO.write(createSourceImage(), "png", imageOutput);
        return new java.io.ByteArrayInputStream(imageOutput.toByteArray());
    }

    private BufferedImage createSourceImage() {
        BufferedImage image = new BufferedImage(1800, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setFont(new Font("PingFang SC", Font.BOLD, 92));
        graphics.drawString("退票规则", 140, 240);
        graphics.setFont(new Font("PingFang SC", Font.PLAIN, 58));
        graphics.drawString("开车前八天以上可免费退票。", 140, 420);
        graphics.drawString("开车前四十八小时以上收取 5% 退票费。", 140, 540);
        graphics.dispose();
        return image;
    }
}
