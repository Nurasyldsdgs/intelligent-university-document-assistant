package com.university.docassistant.service.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {

    @TempDir
    Path root;

    private LocalStorageService storage() throws IOException {
        return new LocalStorageService(root.resolve("uploads").toString());
    }

    @Test
    void storesBytesUnderARandomNameAndLoadsThemBack() throws IOException {
        LocalStorageService storage = storage();
        byte[] content = "Силлабус".getBytes(StandardCharsets.UTF_8);

        String name = storage.store(content);

        assertThat(name).matches("[0-9a-f-]{36}");
        assertThat(root.resolve("uploads").resolve(name)).hasBinaryContent(content);
        Resource loaded = storage.load(name);
        assertThat(loaded.contentLength()).isEqualTo(content.length);
        try (InputStream in = loaded.getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void storesAMultipartUploadWithoutUsingItsName() throws IOException {
        LocalStorageService storage = storage();
        MockMultipartFile upload = new MockMultipartFile("file", "../../etc/passwd.pdf", "application/pdf",
                new byte[]{1, 2, 3});

        String name = storage.store(upload);

        assertThat(name).doesNotContain("passwd").doesNotContain("/");
        assertThat(root.resolve("uploads").resolve(name)).hasBinaryContent(new byte[]{1, 2, 3});
        try (var files = Files.list(root)) {
            assertThat(files).containsExactly(root.resolve("uploads")); // Nothing written outside upload-dir.
        }
    }

    @Test
    void deleteRemovesTheFileAndIgnoresMissingOnes() throws IOException {
        LocalStorageService storage = storage();
        String name = storage.store(new byte[]{42});

        storage.delete(name);
        storage.delete(name); // Already gone: no exception.

        assertThat(root.resolve("uploads").resolve(name)).doesNotExist();
        assertThatThrownBy(() -> storage.load(name)).isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("is missing");
    }

    @Test
    void rejectsPathTraversalInStoredNames() throws IOException {
        LocalStorageService storage = storage();
        Path outside = Files.writeString(root.resolve("secret.txt"), "secret");

        assertThatThrownBy(() -> storage.load("../secret.txt")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete("../secret.txt")).isInstanceOf(IllegalArgumentException.class);
        assertThat(outside).exists();
    }
}
