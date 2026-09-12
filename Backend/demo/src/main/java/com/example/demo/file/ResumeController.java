package com.example.demo.file;

import com.example.demo.file.dto.ResumeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

/**
 * Candidate resume upload/replace/delete plus authorized download. Resumes are
 * private in Cloudinary; {@link #download()} authorizes the owner and redirects
 * to a backend-signed URL rather than exposing the storage URL directly.
 * Ownership always comes from the JWT, never a request param.
 */
@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private final UserFileService userFileService;

    public ResumeController(UserFileService userFileService) {
        this.userFileService = userFileService;
    }

    /** Upload or replace the current candidate's resume (multipart field: file). */
    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResumeResponse upload(@RequestParam("file") MultipartFile file) {
        return userFileService.uploadResume(file);
    }

    /** Metadata for the current candidate's resume (404 if none). */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResumeResponse current() {
        return userFileService.currentResume();
    }

    /** Redirects the authorized owner to a short-lived signed URL for their resume. */
    @GetMapping("/download")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<Void> download() {
        String signedUrl = userFileService.currentResumeSignedUrl();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(signedUrl)).build();
    }

    /** Remove the current candidate's resume. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CANDIDATE')")
    public void delete() {
        userFileService.deleteResume();
    }
}
