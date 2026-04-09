package com.ming.agent12306.knowledge.service;

import com.ming.agent12306.common.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** 知识文档解析服务 */
@Component
public class KnowledgeDocumentParser {

    private static final String PDF_MEDIA_TYPE = "application/pdf";
    private static final String OCR_LANGUAGE = "chi_sim+eng";

    private final Tika tika = new Tika();
    private final Parser autoDetectParser = new AutoDetectParser();
    private final Parser pdfParser = new PDFParser();

    public String parse(byte[] content) {
        try {
            String mediaType = tika.detect(content);
            String text = PDF_MEDIA_TYPE.equals(mediaType)
                    ? parsePdf(content)
                    : parseGeneric(content);
            if (text == null || text.isBlank()) {
                throw new BusinessException("文档解析后内容为空");
            }
            return text;
        } catch (IOException | TikaException | SAXException ex) {
            throw new BusinessException("文档解析失败: " + ex.getMessage());
        }
    }

    private String parseGeneric(byte[] content) throws IOException, TikaException, SAXException {
        BodyContentHandler handler = new BodyContentHandler(-1);
        autoDetectParser.parse(new ByteArrayInputStream(content), handler, new Metadata(), buildOcrParseContext());
        return handler.toString();
    }

    private String parsePdf(byte[] content) throws IOException, TikaException, SAXException {
        String textLayer = extractPdfTextLayer(content);
        if (textLayer != null && !textLayer.isBlank()) {
            return textLayer;
        }

        try (PDDocument document = Loader.loadPDF(content)) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder combined = new StringBuilder();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                String pageText = extractPageImageText(document.getPage(pageIndex));
                if (pageText.isBlank()) {
                    BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, 300, ImageType.RGB);
                    pageText = parseGeneric(toPngBytes(pageImage));
                }
                if (!pageText.isBlank()) {
                    if (!combined.isEmpty()) {
                        combined.append(System.lineSeparator()).append(System.lineSeparator());
                    }
                    combined.append(pageText.trim());
                }
            }
            return combined.toString();
        }
    }

    private String extractPdfTextLayer(byte[] content) throws IOException, TikaException, SAXException {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, PDF_MEDIA_TYPE);
        pdfParser.parse(new ByteArrayInputStream(content), handler, metadata, buildPdfTextParseContext());
        return handler.toString();
    }

    private String extractPageImageText(PDPage page) throws IOException, TikaException, SAXException {
        StringBuilder combined = new StringBuilder();
        appendImageText(page.getResources(), combined);
        return combined.toString();
    }

    private void appendImageText(PDResources resources, StringBuilder combined) throws IOException, TikaException, SAXException {
        if (resources == null) {
            return;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject imageObject) {
                String imageText = parseGeneric(toPngBytes(imageObject.getImage())).trim();
                if (!imageText.isBlank()) {
                    if (!combined.isEmpty()) {
                        combined.append(System.lineSeparator()).append(System.lineSeparator());
                    }
                    combined.append(imageText);
                }
                continue;
            }
            if (xObject instanceof PDFormXObject formXObject) {
                appendImageText(formXObject.getResources(), combined);
            }
        }
    }

    private ParseContext buildOcrParseContext() {
        ParseContext context = new ParseContext();
        context.set(TesseractOCRConfig.class, buildTesseractConfig());
        return context;
    }

    private ParseContext buildPdfTextParseContext() {
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, buildPdfTextParserConfig());
        return context;
    }

    private TesseractOCRConfig buildTesseractConfig() {
        TesseractOCRConfig config = new TesseractOCRConfig();
        config.setLanguage(OCR_LANGUAGE);
        return config;
    }

    private PDFParserConfig buildPdfTextParserConfig() {
        PDFParserConfig config = new PDFParserConfig();
        config.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        return config;
    }

    private byte[] toPngBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
