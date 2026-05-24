package com.LocalService.lsp.util;

import org.springframework.web.multipart.MultipartFile;


public class FileUploadValidator {


    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * Validate file upload for images
     * @param file The file to validate
     * @throws IllegalArgumentException if file validation fails
     */
    public static void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size must not exceed 5MB");
        }
    }

}
