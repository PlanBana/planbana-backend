package com.planbana.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;

@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

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

        return "/uploads/chatrooms/" + fileName;
    }
}
