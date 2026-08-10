package com.fintrack.apiservice.transactionimport.validation;

import com.fintrack.apiservice.transactionimport.exception.InvalidTransactionImportFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Component
public class TransactionImportFileValidator {

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "text/csv",
            "application/csv",
            "application/vnd.ms-excel",
            "text/plain"
    );

    private final long maxFileSizeBytes;

    public TransactionImportFileValidator(@Value("${fintrack.import.max-file-size}") DataSize maxFileSize) {
        this.maxFileSizeBytes = maxFileSize.toBytes();
    }

    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidTransactionImportFileException("A non-empty CSV file is required");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidTransactionImportFileException("The CSV file exceeds the maximum allowed size");
        }

        String originalFileName = normalizeFileName(file.getOriginalFilename());

        if (!originalFileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new InvalidTransactionImportFileException("The uploaded file must use the .csv extension");
        }

        String contentType = normalizeContentType(file.getContentType());

        if (!SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidTransactionImportFileException("The uploaded file has an unsupported content type");
        }

        return originalFileName;
    }

    private String normalizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new InvalidTransactionImportFileException("The uploaded file must have a filename");
        }

        String normalizedPath = originalFileName.replace('\\', '/');
        String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1).trim();

        if (fileName.isBlank()) {
            throw new InvalidTransactionImportFileException("The uploaded file must have a filename");
        }

        if (fileName.length() > 255) {
            throw new InvalidTransactionImportFileException("The uploaded filename cannot exceed 255 characters");
        }

        return fileName;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidTransactionImportFileException("The uploaded file must have a content type");
        }

        return contentType
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}