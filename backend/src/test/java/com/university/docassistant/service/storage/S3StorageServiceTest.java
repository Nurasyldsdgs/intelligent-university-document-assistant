package com.university.docassistant.service.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * No live S3 here: these only build the real client, which catches classpath problems (a missing or mismatched
 * HTTP implementation fails at construction), and check what is refused before any request is sent.
 */
class S3StorageServiceTest {

    @Test
    void buildsAClientForMinioWithoutContactingIt() {
        assertThatNoException().isThrownBy(() -> {
            S3StorageService storage = new S3StorageService("unidocs", "eu-central-1", "http://localhost:9000", true);
            storage.close();
        });
    }

    @Test
    void bucketAndRegionAreRequired() {
        assertThatThrownBy(() -> new S3StorageService("", "eu-central-1", "", false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("app.storage.s3.bucket");
        assertThatThrownBy(() -> new S3StorageService("unidocs", " ", "", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void storedNamesThatAreNotGeneratedKeysNeverReachS3() {
        S3StorageService storage = new S3StorageService("unidocs", "eu-central-1", "http://localhost:9", true);
        try {
            assertThatThrownBy(() -> storage.load("../other-bucket/x")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.load("a/b")).isInstanceOf(IllegalArgumentException.class);
            // delete() never throws, also for a bad name.
            assertThatNoException().isThrownBy(() -> storage.delete("../x"));
            assertThat(storage).isNotNull();
        } finally {
            storage.close();
        }
    }
}
