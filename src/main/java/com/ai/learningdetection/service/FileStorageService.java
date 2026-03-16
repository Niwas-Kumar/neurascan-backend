package com.ai.learningdetection.service;

import com.ai.learningdetection.config.FileStorageConfig;
import com.ai.learningdetection.exception.FileStorageException;
import com.ai.learningdetection.exception.InvalidFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "application/pdf",
            "image/tiff", "image/bmp"
    );

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "pdf", "tiff", "bmp"
    );

    private final FileStorageConfig fileStorageConfig;

    /**
     * Validates and saves a multipart file to the uploads directory.
     * Returns the generated unique filename (UUID-based).
     */
    public String storeFile(MultipartFile file) {
        validateFile(file);

        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown"
        );

        String extension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + extension;

        try {
            Path uploadPath = Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize();
            Path targetLocation = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file: {} -> {}", originalFilename, uniqueFilename);
            return uniqueFilename;
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Could not store file '" + originalFilename + "'. Please try again.", ex);
        }
    }

    /**
     * Returns the absolute path for a stored filename.
     */
    public Path getFilePath(String filename) {
        return Paths.get(fileStorageConfig.getUploadDir()).toAbsolutePath().normalize()
                .resolve(filename);
    }

    /**
     * Deletes a stored file by its unique filename.
     */
    public void deleteFile(String filename) {
        try {
            Path filePath = getFilePath(filename);
            Files.deleteIfExists(filePath);
            log.info("Deleted file: {}", filename);
        } catch (IOException ex) {
            log.warn("Could not delete file: {}", filename);
        }
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Please select a file to upload.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidFileException(
                    "File type not allowed. Accepted types: JPG, PNG, PDF, TIFF, GIF, BMP.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new InvalidFileException(
                        "File extension '." + extension + "' is not allowed.");
            }
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
