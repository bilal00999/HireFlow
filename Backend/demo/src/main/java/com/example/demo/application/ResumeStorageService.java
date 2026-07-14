package com.example.demo.application;

import com.example.demo.common.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Free-tools replacement for S3: stores uploaded resumes on the local
 * filesystem under `app.upload-dir` and returns a public-ish URL path
 * (/uploads/resumes/{file}) that the app serves as a static resource.
 */
@Service
public class ResumeStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final Path root;

    public ResumeStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir, "resumes").toAbsolutePath().normalize();
    }

    /**
     * Persists the resume and returns the stored URL path.
     * Validates that the file is non-empty and a PDF/DOC/DOCX.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Resume file is required");
        }

        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(original);
        ext = ext == null ? "" : ext.toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(ext)
                || (file.getContentType() != null
                    && !ALLOWED_CONTENT_TYPES.contains(file.getContentType()))) {
            throw new BadRequestException("Resume file must be PDF or DOCX");
        }

        String filename = UUID.randomUUID() + "." + ext;
        try {
            Files.createDirectories(root);
            Path target = root.resolve(filename).normalize();
            // Guard against path traversal via a crafted filename.
            if (!target.getParent().equals(root)) {
                throw new BadRequestException("Invalid file name");
            }
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store resume", e);
        }

        return "/uploads/resumes/" + filename;
    }
}
