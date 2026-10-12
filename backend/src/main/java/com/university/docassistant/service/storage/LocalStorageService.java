package com.university.docassistant.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stores uploaded files as-is under {@code app.storage.upload-dir}, each under a random UUID name.
 * Content is streamed in both directions, so a file is never held in memory as a whole.
 * The default driver ({@code app.storage.driver=local} or unset).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.driver", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path uploadDir;

    public LocalStorageService(@Value("${app.storage.upload-dir}") String uploadDir) throws IOException {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
        log.info("Document storage at {}", this.uploadDir);
    }

    /** Writes the upload to disk; returns the generated stored file name. */
    @Override
    public String store(MultipartFile file) {
        String storedFileName = UUID.randomUUID().toString();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, resolve(storedFileName));
        } catch (IOException ex) {
            delete(storedFileName);
            throw new UncheckedIOException("Could not write file " + storedFileName, ex);
        }
        return storedFileName;
    }

    /** Writes content already in memory (e.g. a file from a ZIP archive); returns the generated stored file name. */
    @Override
    public String store(byte[] content) {
        String storedFileName = UUID.randomUUID().toString();
        try {
            Files.write(resolve(storedFileName), content);
        } catch (IOException ex) {
            delete(storedFileName);
            throw new UncheckedIOException("Could not write file " + storedFileName, ex);
        }
        return storedFileName;
    }

    @Override
    public Resource load(String storedFileName) {
        Path path = resolve(storedFileName);
        if (!Files.isReadable(path)) {
            throw new UncheckedIOException(new IOException("Stored file " + storedFileName + " is missing"));
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storedFileName) {
        try {
            Files.deleteIfExists(resolve(storedFileName));
        } catch (IOException ex) {
            // The DB row is already gone; an orphaned file is harmless, so only log it.
            log.warn("Could not delete file {}: {}", storedFileName, ex.getMessage());
        }
    }

    private Path resolve(String storedFileName) {
        Path path = uploadDir.resolve(storedFileName).normalize();
        if (!path.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Invalid stored file name");
        }
        return path;
    }
}
