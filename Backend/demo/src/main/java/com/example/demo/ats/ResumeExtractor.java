package com.example.demo.ats;

import com.example.demo.application.ResumeStorageService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Extracts plain text from a stored resume so the ATS can score it.
 * Supports PDF (PDFBox) and DOC/DOCX (Apache POI). The extracted text is
 * fed to the LLM prompt; formatting is intentionally discarded.
 */
@Service
public class ResumeExtractor {

    private final ResumeStorageService storage;

    public ResumeExtractor(ResumeStorageService storage) {
        this.storage = storage;
    }

    /** Reads the resume at the given stored URL and returns its text content. */
    public String extractText(String resumeUrl) {
        Path file = storage.resolve(resumeUrl);
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf")) {
                return extractPdf(file);
            } else if (name.endsWith(".docx")) {
                return extractDocx(file);
            } else if (name.endsWith(".doc")) {
                return extractDoc(file);
            }
            throw new ResumeExtractionException("Unsupported resume format: " + name);
        } catch (IOException e) {
            throw new ResumeExtractionException("Failed to read resume file", e);
        }
    }

    private String extractPdf(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            return new PDFTextStripper().getText(doc).strip();
        }
    }

    private String extractDocx(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText().strip();
        }
    }

    private String extractDoc(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             WordExtractor extractor = new WordExtractor(in)) {
            return extractor.getText().strip();
        }
    }
}
