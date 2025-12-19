package com.planbana.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.planbana.backend.admin.settings.SystemSettings;
import com.planbana.backend.admin.settings.SystemSettingsService;

import java.io.IOException;
import java.nio.file.*;

@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

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

}
