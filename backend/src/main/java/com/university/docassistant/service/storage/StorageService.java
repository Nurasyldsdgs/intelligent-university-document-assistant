package com.university.docassistant.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Where uploaded files live: {@link LocalStorageService} (default, {@code app.storage.driver=local}) or
 * {@link S3StorageService} ({@code app.storage.driver=s3}). Files are stored as-is under a random name that callers
 * keep (documents.stored_file_name); user-supplied names never reach the storage.
 */
public interface StorageService {

    /** Streams the upload into storage; returns the generated stored file name. */
    String store(MultipartFile file);

    /** Stores content already in memory (e.g. a file from a ZIP archive); returns the generated stored file name. */
    String store(byte[] content);

    /**
     * The stored file, readable once; {@code contentLength()} is known without reading it.
     * Throws UncheckedIOException when the file is missing.
     */
    Resource load(String storedFileName);

    /** Removes the file; failures are logged, never thrown (the DB row is already gone, an orphan is harmless). */
    void delete(String storedFileName);
}
