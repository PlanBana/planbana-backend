package com.planbana.backend.storage;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UrlPathHelper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves files previously stored under {@code file.upload-dir} (avatars,
 * chatroom images and chat attachments) via {@code GET /uploads/**}.
 *
 * <p>The previous approach used Spring's static {@code file:} resource handler,
 * which is unreliable for Windows paths containing spaces (e.g.
 * {@code E:\Planbana Workspace\...}) and returned 404 even when the file
 * existed. This endpoint resolves the absolute filesystem path directly, guards
 * against path traversal, and sets a correct {@code Content-Type} +
 * {@code Content-Disposition} so images and PDFs open in the browser.
 */
@RestController
public class UploadsController {

    private final Path uploadRoot;

    public UploadsController(@Value("${file.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /** Must be public so Spring Security can (and is not) required to check it. */
    @GetMapping(value = "/uploads/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String pathWithinApp = new UrlPathHelper().getPathWithinApplication(request); // already URI-decoded
        int prefixEnd = "/uploads".length();
        if (pathWithinApp.length() <= prefixEnd || pathWithinApp.substring(prefixEnd).charAt(0) != '/') {
            throw notFound();
        }

        // relative segment after "/uploads/", e.g. "chat_attachments/{roomId}/file"
        String relative = pathWithinApp.substring(prefixEnd + 1);

        Path file = uploadRoot.resolve(relative).normalize();
        if (!file.startsWith(uploadRoot)) {
            throw notFound(); // path traversal
        }
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw notFound();
        }

        String filename = file.getFileName().toString();
        MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeHeader(filename) + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .header(HttpHeaders.CONTENT_LENGTH, Long.toString(file.toFile().length()))
                .body(new FileSystemResource(file.toFile()));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }

    /** Prevents header injection through a crafted file name. */
    private static String sanitizeHeader(String value) {
        return value.replace("\"", "").replaceAll("[\\r\\n]", "").trim();
    }
}