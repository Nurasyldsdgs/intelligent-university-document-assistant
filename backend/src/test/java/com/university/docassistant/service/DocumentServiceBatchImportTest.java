package com.university.docassistant.service;

import com.university.docassistant.config.PrivacyProperties;
import com.university.docassistant.dto.UploadDtos.BatchUploadResponse;
import com.university.docassistant.entity.BatchImport;
import com.university.docassistant.entity.Course;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.repository.AssignmentRepository;
import com.university.docassistant.repository.BatchImportRepository;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.repository.TaskRepository;
import com.university.docassistant.security.AccessControlService;
import com.university.docassistant.security.Permission;
import com.university.docassistant.service.DocumentParserService.ExtractedText;
import com.university.docassistant.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every ZIP upload leaves a BatchImport row that matches what was really imported. */
class DocumentServiceBatchImportTest {

    private static final String EMAIL = "teacher@uni.kz";

    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final StorageService storage = mock(StorageService.class);
    private final DocumentParserService parser = mock(DocumentParserService.class);
    private final AiClientService ai = mock(AiClientService.class);
    private final AuditService audit = mock(AuditService.class);
    private final BatchImportRepository imports = mock(BatchImportRepository.class);
    private final DocumentService service = new DocumentService(documents, mock(TaskRepository.class),
            mock(AssignmentRepository.class), access, storage, parser, mock(DocumentAnalysisService.class), ai,
            mock(DocumentChunkService.class), audit, mock(DocumentClassificationService.class),
            mock(DocumentProcessingService.class), imports, mock(AssignmentService.class), new DocumentPrivacyService(new PrivacyProperties(null, null)));

    private final User teacher = new User();
    private final Course course = Course.builder().id(10L).title("Algorithms").build();
    /** processedFiles at each save of the BatchImport (the same object is saved twice and mutated in between). */
    private final List<Integer> processedAtSave = new ArrayList<>();
    private final List<Document> savedDocuments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        teacher.setId(2L);
        teacher.setRole(Role.ROLE_TEACHER);
        teacher.setEmail(EMAIL);
        when(access.currentUser(EMAIL)).thenReturn(teacher);
        when(access.authorizeCourse(teacher, 10L, Permission.UPLOAD_DOCUMENT)).thenReturn(course);
        when(parser.extractText(any(InputStream.class), anyString(), anyString()))
                .thenReturn(new ExtractedText("Лекция", false));
        when(storage.store(any(byte[].class))).thenReturn("stored-name");
        AtomicLong ids = new AtomicLong(100);
        when(documents.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(ids.incrementAndGet());
            savedDocuments.add(document);
            return document;
        });
        when(imports.save(any(BatchImport.class))).thenAnswer(invocation -> {
            BatchImport batchImport = invocation.getArgument(0);
            batchImport.setId(7L);
            processedAtSave.add(batchImport.getProcessedFiles());
            return batchImport;
        });
    }

    @Test
    void fifteenValidFilesGiveOneImportWithAllDocumentsLinked() throws IOException {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            names.add("lectures/lecture" + i + ".txt");
        }

        BatchUploadResponse response = service.uploadBatch(zip(names), null, null, 10L, EMAIL);

        BatchImport batchImport = savedImport();
        assertThat(batchImport.getTotalFiles()).isEqualTo(15);
        assertThat(batchImport.getProcessedFiles()).isEqualTo(15);
        assertThat(batchImport.getCourse()).isSameAs(course);
        assertThat(batchImport.getFaculty()).isSameAs(teacher);
        assertThat(batchImport.getArchiveName()).isEqualTo("lectures.zip");
        assertThat(processedAtSave).containsExactly(0, 15);
        assertThat(savedDocuments).hasSize(15).allSatisfy(document -> {
            assertThat(document.getBatchImport()).isSameAs(batchImport);
            assertThat(document.getCourse()).isSameAs(course);
        });
        assertThat(response.batchImportId()).isEqualTo(7L);
        assertThat(response.uploaded()).hasSize(15);
        verify(audit).record(eq(EMAIL), eq(AuditService.BATCH_IMPORTED), anyString());
    }

    @Test
    void zipSlipEntryIsSkippedButCounted() throws IOException {
        BatchUploadResponse response = service.uploadBatch(
                zip(List.of("a.txt", "b.txt", "../../evil.txt")), null, null, 10L, EMAIL);

        BatchImport batchImport = savedImport();
        assertThat(batchImport.getTotalFiles()).isEqualTo(3);
        assertThat(batchImport.getProcessedFiles()).isEqualTo(2);
        assertThat(response.uploaded()).hasSize(2);
        assertThat(response.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.fileName()).isEqualTo("../../evil.txt");
            assertThat(skipped.reason()).isEqualTo("Небезопасный путь в архиве");
        });
        assertThat(savedDocuments).extracting(Document::getOriginalFileName).containsExactly("a.txt", "b.txt");
    }

    @Test
    void aFileWhoseContentDoesNotMatchItsExtensionIsSkippedButCounted() throws IOException {
        // zip() writes text into every entry, so "fake.pdf" holds plain text.
        BatchUploadResponse response = service.uploadBatch(zip(List.of("a.txt", "fake.pdf")), null, null, 10L, EMAIL);

        BatchImport batchImport = savedImport();
        assertThat(batchImport.getTotalFiles()).isEqualTo(2);
        assertThat(batchImport.getProcessedFiles()).isEqualTo(1);
        assertThat(response.skipped()).singleElement().satisfies(skipped -> {
            assertThat(skipped.fileName()).isEqualTo("fake.pdf");
            assertThat(skipped.reason()).isEqualTo("Содержимое файла не соответствует расширению");
        });
        assertThat(savedDocuments).singleElement().satisfies(document ->
                assertThat(document.getContentType()).isEqualTo("text/plain"));
    }

    @Test
    void anImportWhereEverythingIsSkippedIsStillRecorded() throws IOException {
        BatchUploadResponse response = service.uploadBatch(zip(List.of("virus.exe", "../x.txt")), null, null, 10L,
                EMAIL);

        BatchImport batchImport = savedImport();
        assertThat(batchImport.getTotalFiles()).isEqualTo(2);
        assertThat(batchImport.getProcessedFiles()).isZero();
        assertThat(response.uploaded()).isEmpty();
        assertThat(response.batchImportId()).isEqualTo(7L);
    }

    @Test
    void aFailingFileStillLeavesTheRealCountInTheImport() throws IOException {
        AtomicLong calls = new AtomicLong();
        when(storage.store(any(byte[].class))).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 3) {
                throw new IllegalStateException("disk full");
            }
            return "stored-name";
        });

        assertThatThrownBy(() -> service.uploadBatch(zip(List.of("a.txt", "b.txt", "c.txt", "d.txt")), null, null,
                10L, EMAIL)).hasMessage("disk full");

        BatchImport batchImport = savedImport();
        assertThat(batchImport.getTotalFiles()).isEqualTo(4);
        assertThat(batchImport.getProcessedFiles()).isEqualTo(2);
        assertThat(processedAtSave).containsExactly(0, 2);
    }

    private BatchImport savedImport() {
        ArgumentCaptor<BatchImport> saved = ArgumentCaptor.forClass(BatchImport.class);
        verify(imports, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0)).isSameAs(saved.getAllValues().get(1));
        return saved.getValue();
    }

    private static MockMultipartFile zip(List<String> entryNames) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (String name : entryNames) {
                out.putNextEntry(new ZipEntry(name));
                out.write(("Содержимое " + name).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return new MockMultipartFile("file", "lectures.zip", "application/zip", bytes.toByteArray());
    }
}
