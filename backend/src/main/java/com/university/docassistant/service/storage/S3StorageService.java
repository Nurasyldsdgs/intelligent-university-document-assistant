package com.university.docassistant.service.storage;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores uploaded files as objects in an S3 bucket ({@code app.storage.driver=s3}), keyed by the same random UUID
 * names the local driver uses. Works with AWS S3 and S3-compatible servers such as MinIO ({@code endpoint} +
 * {@code path-style-access}). Credentials come from the AWS default provider chain (AWS_ACCESS_KEY_ID /
 * AWS_SECRET_ACCESS_KEY, ~/.aws/credentials, an instance or container role ...), never from application.yml.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.driver", havingValue = "s3")
public class S3StorageService implements StorageService {

    /** Stored names are generated UUIDs; anything else never becomes an object key. */
    private static final Pattern STORED_NAME = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final String CONTENT_TYPE = "application/octet-stream";

    private final S3Client s3;
    private final String bucket;

    public S3StorageService(@Value("${app.storage.s3.bucket:}") String bucket,
                            @Value("${app.storage.s3.region:}") String region,
                            @Value("${app.storage.s3.endpoint:}") String endpoint,
                            @Value("${app.storage.s3.path-style-access:false}") boolean pathStyleAccess) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(region)) {
            throw new IllegalStateException("app.storage.driver=s3 needs app.storage.s3.bucket and app.storage.s3.region");
        }
        this.bucket = bucket.strip();
        S3ClientBuilder builder = S3Client.builder()
                // Set explicitly: the JDK-based client is the only HTTP implementation on the classpath (see pom.xml).
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .region(Region.of(region.strip()))
                .forcePathStyle(pathStyleAccess);
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint.strip()));
        }
        this.s3 = builder.build();
        log.info("Document storage in S3 bucket {} ({}{})", this.bucket, region.strip(),
                StringUtils.hasText(endpoint) ? ", endpoint " + endpoint.strip() : "");
    }

    /** For tests: an already configured client. */
    S3StorageService(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    /** Streams the upload with its known length, so nothing is buffered in memory. */
    @Override
    public String store(MultipartFile file) {
        String key = UUID.randomUUID().toString();
        try (InputStream in = file.getInputStream()) {
            s3.putObject(builder -> builder.bucket(bucket).key(key).contentType(CONTENT_TYPE)
                    .contentLength(file.getSize()), RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not write file " + key, ex);
        } catch (SdkException ex) {
            throw new UncheckedIOException("Could not write file " + key, new IOException(ex.getMessage(), ex));
        }
        return key;
    }

    @Override
    public String store(byte[] content) {
        String key = UUID.randomUUID().toString();
        try {
            s3.putObject(builder -> builder.bucket(bucket).key(key).contentType(CONTENT_TYPE)
                    .contentLength((long) content.length), RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            throw new UncheckedIOException("Could not write file " + key, new IOException(ex.getMessage(), ex));
        }
        return key;
    }

    /**
     * A HEAD request now (existence and length); the object itself is fetched only when the resource is read, so a
     * caller that bails out before writing the response (e.g. "preview not available") leaves no open connection.
     * The returned resource streams the object and reports its contentLength, which DocumentController needs.
     */
    @Override
    public Resource load(String storedFileName) {
        String key = key(storedFileName);
        HeadObjectResponse head;
        try {
            head = s3.headObject(builder -> builder.bucket(bucket).key(key));
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new UncheckedIOException(new IOException("Stored file " + storedFileName + " is missing"));
            }
            throw new UncheckedIOException(new IOException("Could not read file " + storedFileName, ex));
        } catch (SdkException ex) {
            throw new UncheckedIOException(new IOException("Could not read file " + storedFileName, ex));
        }
        return new S3ObjectResource(key, head.contentLength());
    }

    @Override
    public void delete(String storedFileName) {
        try {
            s3.deleteObject(builder -> builder.bucket(bucket).key(key(storedFileName)));
        } catch (SdkException | IllegalArgumentException ex) {
            // The DB row is already gone; an orphaned object is harmless, so only log it.
            log.warn("Could not delete file {}: {}", storedFileName, ex.getMessage());
        }
    }

    @PreDestroy
    void close() {
        s3.close();
    }

    /**
     * The object, fetched on the first {@link #getInputStream()}; readable once, like an InputStreamResource.
     * (Spring 6.1.4 has no lazy InputStreamResource constructor yet, hence this small class.)
     */
    private final class S3ObjectResource extends AbstractResource {

        private final String key;
        private final long length;

        private S3ObjectResource(String key, long length) {
            this.key = key;
            this.length = length;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return s3.getObject(builder -> builder.bucket(bucket).key(key));
            } catch (SdkException ex) {
                throw new IOException("Could not read file " + key, ex);
            }
        }

        @Override
        public long contentLength() {
            return length;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public String getDescription() {
            return "S3 object " + bucket + "/" + key;
        }
    }

    private static String key(String storedFileName) {
        if (storedFileName == null || !STORED_NAME.matcher(storedFileName).matches()) {
            throw new IllegalArgumentException("Invalid stored file name");
        }
        return storedFileName;
    }
}
