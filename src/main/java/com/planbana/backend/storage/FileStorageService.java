package com.planbana.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.planbana.backend.admin.settings.SystemSettings;
import com.planbana.backend.admin.settings.SystemSettingsService;
import com.planbana.backend.chat.AttachmentUploadResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.chat.attachment.max-image-mb:10}")
    private long chatImageMaxMb;

    @Value("${file.chat.attachment.max-pdf-mb:15}")
    private long chatPdfMaxMb;

    @Value("${file.chat.attachment.max-doc-mb:15}")
    private long chatDocMaxMb;

    private final SystemSettingsService settingsService;

    public FileStorageService(SystemSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    private void validateSize(MultipartFile file) {
        SystemSettings s = settingsService.get();
        long maxBytes = s.getEventImageMaxSizeMb() * 1024L * 1024L;

        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "IMAGE_TOO_LARGE");
        }
    }

    public String saveAvatar(MultipartFile file, String userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("Empty or missing file");
        }

        Path uploadPath = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        String cleanName = Path.of(file.getOriginalFilename()).getFileName().toString().replaceAll("\\s+", "_");
        String fileName = userId + "_" + System.currentTimeMillis() + "_" + cleanName;

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        validateSize(file);

        return "/uploads/avatars/" + fileName;
    }

    // 👇 For chatroom DP uploads
    public String saveChatroomImage(MultipartFile file, String chatRoomId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("Empty or missing file");
        }

        Path uploadPath = Paths.get(uploadDir, "chatrooms").toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        String cleanName = Path.of(file.getOriginalFilename()).getFileName().toString().replaceAll("\\s+", "_");
        String fileName = chatRoomId + "_" + System.currentTimeMillis() + "_" + cleanName;

        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        validateSize(file);

        return "/uploads/chatrooms/" + fileName;
    }

    // ============================================================
    // Chat attachments (images + documents)
    // ============================================================

    /** Allowed MIME types and the storage category each maps to. */
    private static final Map<String, String> ALLOWED_CHAT_MIME = Map.ofEntries(
            Map.entry("image/jpeg", "image"),
            Map.entry("image/png", "image"),
            Map.entry("image/webp", "image"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/msword", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "doc"),
            Map.entry("application/vnd.ms-excel", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "doc"),
            Map.entry("application/vnd.ms-powerpoint", "doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "doc"),
            Map.entry("text/plain", "doc"));

    /**
     * Validates a chat attachment (MIME allow-list, magic-byte/content check,
     * per-category size limit) and copies it into object storage under
     * {@code /uploads/chat_attachments/{eventId}/...}.
     *
     * @return metadata for the client to forward over the chat WebSocket.
     */
    public AttachmentUploadResult saveChatAttachment(MultipartFile file, String eventId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }

        String category = resolveChatAttachmentCategory(file);
        long maxBytes = chatMaxBytes(category);
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ATTACHMENT_TOO_LARGE exceeds " + (maxBytes / (1024L * 1024L)) + "MB");
        }

        String dirKey = safeDir(eventId);
        Path uploadPath = Paths.get(uploadDir, "chat_attachments", dirKey).toAbsolutePath().normalize();
        if (!uploadPath.startsWith(Paths.get(uploadDir, "chat_attachments").toAbsolutePath().normalize())) {
            throw new IOException("Invalid attachment directory");
        }
        Files.createDirectories(uploadPath);

        String cleanName = Path.of(file.getOriginalFilename()).getFileName().toString().replaceAll("\\s+", "_");
        String storageFileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + cleanName;

        Path filePath = uploadPath.resolve(storageFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String storageKey = "chat_attachments/" + dirKey + "/" + storageFileName;
        String messageType = ("image".equals(category)) ? "IMAGE" : "FILE";

        AttachmentUploadResult result = new AttachmentUploadResult();
        result.setMessageType(messageType);
        result.setAttachmentUrl("/uploads/" + storageKey);
        result.setAttachmentName(cleanName);
        result.setAttachmentMimeType(file.getContentType());
        result.setAttachmentSize(file.getSize());
        result.setAttachmentStorageKey(storageKey);
        return result;
    }

    private String resolveChatAttachmentCategory(MultipartFile file) throws IOException {
        String mime = file.getContentType();
        String category = ALLOWED_CHAT_MIME.get(mime);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
        }
        if (!matchesMagicBytes(file, category, mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FILE_CONTENT_MISMATCH");
        }
        return category;
    }

    /**
     * Light content verification via magic bytes. Only image/PDF headers are
     * sniffed; office documents have no reliable signature so they rely on the
     * declared MIME type (which is further gated by the allow-list).
     */
    private boolean matchesMagicBytes(MultipartFile file, String category, String mime) throws IOException {
        byte[] head = file.getBytes();

        if (category.equals("image")) {
            if ("image/png".equals(mime)) {
                byte[] sig = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
                return startsWith(head, sig);
            }
            if ("image/jpeg".equals(mime)) {
                return head.length >= 3
                        && (head[0] & 0xFF) == 0xFF
                        && (head[1] & 0xFF) == 0xD8
                        && (head[2] & 0xFF) == 0xFF;
            }
            if ("image/webp".equals(mime)) {
                return head.length >= 12
                        && startsWith(head, "RIFF".getBytes(StandardCharsets.US_ASCII))
                        && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            }
            return false;
        }

        if ("application/pdf".equals(mime)) {
            return head.length >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
        }

        // Documents (.doc/.xls/.ppt + OOXML + .txt): no reliable global signature.
        return true;
    }

    private long chatMaxBytes(String category) {
        switch (category) {
            case "image":
                return chatImageMaxMb * 1024L * 1024L;
            case "pdf":
                return chatPdfMaxMb * 1024L * 1024L;
            case "doc":
            default:
                return chatDocMaxMb * 1024L * 1024L;
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Sanitises a path segment so it cannot escape the storage root. */
    private static String safeDir(String value) {
        if (value == null) {
            return "default";
        }
        String clean = value.replaceAll("[\\\\/.:]", "").trim();
        return clean.isEmpty() ? "default" : clean;
    }

}
