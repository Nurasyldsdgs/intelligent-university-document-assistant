package com.university.docassistant.service;

import com.university.docassistant.dto.AssignmentDtos.AssignmentResponse;
import com.university.docassistant.dto.DocumentAnalysisResponse;
import com.university.docassistant.dto.DocumentResponse;
import com.university.docassistant.dto.DocumentTextResponse;
import com.university.docassistant.dto.PageResponse;
import com.university.docassistant.dto.UploadDtos.BatchUploadResponse;
import com.university.docassistant.dto.UploadDtos.SkippedFile;
import com.university.docassistant.dto.UploadDtos.UploadedDocument;
import com.university.docassistant.entity.Course;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.DocumentCategory;
import com.university.docassistant.entity.DocumentType;
import com.university.docassistant.entity.DocumentVisibility;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.repository.AssignmentRepository;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.repository.DocumentSummary;
import com.university.docassistant.repository.TaskRepository;
import com.university.docassistant.security.AccessControlService;
import com.university.docassistant.security.AccessControlService.Scope;
import com.university.docassistant.security.Permission;
import com.university.docassistant.service.DocumentParserService.ExtractedText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Documents always belong to a course, and every access goes through {@link AccessControlService}:
 * view - the course's enrolled students (not TEACHER_ONLY documents), its teacher, ADMIN;
 * upload, delete, analyze - the course's teacher or ADMIN. Documents left from before this rule without a course
 * are reachable by ADMIN only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_CONTENT_TYPE_LENGTH = 150;
    private static final int MAX_QUERY_LENGTH = 200;
    /** Per document, whether uploaded alone, with others or inside a ZIP archive. */
    static final long MAX_DOCUMENT_BYTES = 20L * 1024 * 1024;
    private static final int MAX_FILES_PER_REQUEST = 50;
    /** Formats accepted by the multi-file and ZIP uploads, with the content type stored for each. */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "pdf", MediaType.APPLICATION_PDF_VALUE,
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "doc", "application/msword",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "txt", MediaType.TEXT_PLAIN_VALUE,
            "png", MediaType.IMAGE_PNG_VALUE,
            "jpg", MediaType.IMAGE_JPEG_VALUE,
            "jpeg", MediaType.IMAGE_JPEG_VALUE);

    private final DocumentRepository documentRepository;
    private final TaskRepository taskRepository;
    private final AssignmentRepository assignmentRepository;
    private final AccessControlService access;
    private final FileStorageService fileStorageService;
    private final DocumentParserService documentParserService;
    private final DocumentAnalysisService documentAnalysisService;
    private final AiClientService aiClientService;
    private final DocumentChunkService documentChunkService;
    private final AuditService auditService;
    private final DocumentClassificationService classificationService;

    public record DocumentContent(Document document, Resource content) {
    }

    /**
     * One file. Without {@code category} the AI classifier picks the type (OTHER if the AI is unavailable).
     * For the course teacher or ADMIN only, checked before the file is read.
     */
    public DocumentResponse upload(MultipartFile file, DocumentCategory category, DocumentVisibility visibility,
                                   Long courseId, String email) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Файл пустой");
        }
        UploadTarget target = authorizeUpload(courseId, visibility, email);
        String fileName = sanitizeFileName(file.getOriginalFilename());
        byte[] content = readUpload(file, fileName);
        return store(target, fileName, content, file.getContentType(), category).document();
    }

    /**
     * Several files in one request (US-04). Unsupported, empty or oversized files are skipped and reported; the rest
     * become documents of the course, each classified by the AI unless {@code category} is given.
     */
    public BatchUploadResponse uploadMultiple(List<MultipartFile> files, DocumentCategory category,
                                              DocumentVisibility visibility, Long courseId, String email) {
        UploadTarget target = authorizeUpload(courseId, visibility, email);
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("Не выбрано ни одного файла");
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw ApiException.badRequest("За один раз можно загрузить не больше " + MAX_FILES_PER_REQUEST + " файлов");
        }
        List<UploadedDocument> uploaded = new ArrayList<>();
        List<SkippedFile> skipped = new ArrayList<>();
        for (MultipartFile file : files) {
            String fileName = sanitizeFileName(file.getOriginalFilename());
            String problem = file.isEmpty() ? "Пустой файл"
                    : !isAllowedDocument(fileName) ? "Неподдерживаемый формат файла"
                    : file.getSize() > MAX_DOCUMENT_BYTES ? "Файл больше " + MAX_DOCUMENT_BYTES / (1024 * 1024) + " МБ"
                    : null;
            if (problem != null) {
                skipped.add(new SkippedFile(fileName, problem));
                continue;
            }
            uploaded.add(store(target, fileName, readUpload(file, fileName), file.getContentType(), category));
        }
        return new BatchUploadResponse(uploaded, skipped);
    }

    /**
     * A ZIP archive (US-06): its supported files become documents of the course, the rest is reported as skipped.
     * Rights are checked before the archive is opened; see ZipArchiveExtractor for the Zip Slip and size guards.
     */
    public BatchUploadResponse uploadBatch(MultipartFile archive, DocumentCategory category,
                                           DocumentVisibility visibility, Long courseId, String email) {
        UploadTarget target = authorizeUpload(courseId, visibility, email);
        if (archive == null || archive.isEmpty()) {
            throw ApiException.badRequest("Архив пустой");
        }
        String archiveName = sanitizeFileName(archive.getOriginalFilename());
        if (!archiveName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw ApiException.badRequest("Ожидается ZIP-архив (.zip)");
        }
        ZipArchiveExtractor.Result extracted;
        try {
            extracted = ZipArchiveExtractor.extract(readUpload(archive, archiveName),
                    DocumentService::isAllowedDocument, MAX_DOCUMENT_BYTES);
        } catch (IllegalStateException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        List<UploadedDocument> uploaded = new ArrayList<>();
        for (ZipArchiveExtractor.ExtractedFile file : extracted.files()) {
            uploaded.add(store(target, sanitizeFileName(file.fileName()), file.content(), null, category));
        }
        log.info("User {} imported {} document(s) from archive '{}' into course {} ({} skipped)",
                target.owner().getEmail(), uploaded.size(), archiveName, courseId, extracted.skipped().size());
        return new BatchUploadResponse(uploaded, extracted.skipped());
    }

    private record UploadTarget(User owner, Course course, DocumentVisibility visibility) {
    }

    /**
     * Only the course's teacher or ADMIN may add documents to it; students get 403 before anything is read; they
     * upload only as a submission for their task (SubmissionService).
     */
    private UploadTarget authorizeUpload(Long courseId, DocumentVisibility visibility, String email) {
        User owner = access.currentUser(email);
        if (!Permission.granted(owner.getRole(), Permission.UPLOAD_DOCUMENT)) {
            auditService.record(owner.getEmail(), AuditService.ACCESS_DENIED, "UPLOAD_DOCUMENT on course " + courseId);
            throw ApiException.forbidden("Загружать документы курса может только его преподаватель или администратор. "
                    + "Студенты прикрепляют файлы как решение к своей задаче (раздел «Мои задачи»)");
        }
        if (courseId == null) {
            throw ApiException.badRequest("Выберите курс: каждый документ принадлежит курсу");
        }
        Course course = access.authorizeCourse(owner, courseId, Permission.UPLOAD_DOCUMENT);
        // PUBLIC_ALL no longer reaches beyond the course; it is stored as what it now means.
        DocumentVisibility effective = visibility == null || visibility == DocumentVisibility.PUBLIC_ALL
                ? DocumentVisibility.COURSE_STUDENTS
                : visibility;
        return new UploadTarget(owner, course, effective);
    }

    /**
     * Stores one file as a document of the target course: text extraction, AI type classification when no category
     * was chosen, Q&A chunks, and - for a SYLLABUS or ASSIGNMENT - the background assignment extraction.
     */
    private UploadedDocument store(UploadTarget target, String fileName, byte[] content, String declaredType,
                                   DocumentCategory chosenCategory) {
        if (content.length > MAX_DOCUMENT_BYTES) {
            throw ApiException.badRequest("Файл «" + fileName + "» больше " + MAX_DOCUMENT_BYTES / (1024 * 1024) + " МБ");
        }
        String contentType = declaredType == null ? contentTypeOf(fileName) : sanitizeContentType(declaredType);

        // A failed parse only leaves the document without text; it never fails the upload.
        ExtractedText extracted = extractText(content, fileName, contentType);

        DocumentCategory category = chosenCategory;
        boolean aiClassified = false;
        if (category == null) {
            Optional<DocumentCategory> detected = classificationService.classify(fileName, extracted.text());
            category = detected.orElse(DocumentCategory.OTHER);
            aiClassified = detected.isPresent();
        }

        String storedFileName = fileStorageService.store(content);
        Document document;
        try {
            document = documentRepository.save(Document.builder()
                    .originalFileName(fileName)
                    .storedFileName(storedFileName)
                    .contentType(contentType)
                    .fileSize((long) content.length)
                    .extractedText(extracted.text())
                    .textSnippet(DocumentParserService.snippet(extracted.text()))
                    .textTruncated(extracted.truncated())
                    .category(category)
                    .type(DocumentType.fromCategory(category))
                    .visibility(target.visibility())
                    .course(target.course())
                    .owner(target.owner())
                    .build());
            log.info("User {} uploaded document {} to course {} ({} bytes, {}{}, {}, {} chars of text{})",
                    target.owner().getEmail(), document.getId(), target.course().getId(), content.length, category,
                    aiClassified ? " by AI" : "", target.visibility(),
                    extracted.text() == null ? 0 : extracted.text().length(),
                    extracted.truncated() ? ", truncated" : "");
        } catch (RuntimeException ex) {
            fileStorageService.delete(storedFileName);
            throw ex;
        }
        auditService.record(target.owner().getEmail(), AuditService.DOCUMENT_UPLOADED,
                "Document " + document.getId() + " \"" + fileName + "\" (" + category + (aiClassified ? ", AI" : "")
                        + ") in course " + target.course().getId());

        if (extracted.text() != null) {
            try {
                documentChunkService.index(document.getId(), extracted.text());
            } catch (RuntimeException ex) {
                // Q&A is unavailable for this document until DocumentChunkBackfill retries at the next start.
                log.warn("Could not chunk document {} for Q&A: {}", document.getId(), ex.toString());
            }
        }

        // The row is committed by now (upload runs without a transaction), so the background job can read it.
        if (extracted.text() != null && DocumentAnalysisService.AUTO_ANALYZED.contains(category)
                && aiClientService.isConfigured()) {
            submitAnalysis(document.getId(), false);
        }
        return new UploadedDocument(toResponse(DocumentSummary.of(document), target.owner(), true), aiClassified);
    }

    private static byte[] readUpload(MultipartFile file, String fileName) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read upload '" + fileName + "'", ex);
        }
    }

    static boolean isAllowedDocument(String fileName) {
        return ALLOWED_TYPES.containsKey(extensionOf(fileName));
    }

    static String contentTypeOf(String fileName) {
        return ALLOWED_TYPES.getOrDefault(extensionOf(fileName), MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Queues an AI analysis that replaces the assignments found in the document; for the course teacher or ADMIN.
     * Throws 503 when the AI is not configured or the analysis queue is full, 400 when there is no text.
     */
    @Transactional(readOnly = true)
    public void requestAnalysis(Long id, String email) {
        User user = access.currentUser(email);
        Document document = access.authorizeDocument(user, id, Permission.ANALYZE_DOCUMENT);
        if (!aiClientService.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI analysis is not configured");
        }
        if (document.getTextSnippet() == null) {
            throw ApiException.badRequest("The document has no extracted text to analyze");
        }
        if (!submitAnalysis(id, true)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI analysis queue is full, try again later");
        }
        log.info("User {} requested AI analysis of document {}", user.getEmail(), id);
    }

    /**
     * The document's latest AI summary and topics, plus what the current user can do about it, so the UI can offer
     * "analyze" only when it would be accepted.
     */
    @Transactional(readOnly = true)
    public DocumentAnalysisResponse analysis(Long id, String email) {
        User user = access.currentUser(email);
        Document document = access.authorizeDocument(user, id, Permission.VIEW_DOCUMENT);
        List<String> topics = document.getAiTopics() == null ? List.of() : List.of(document.getAiTopics().split("\n"));
        return new DocumentAnalysisResponse(
                document.getAiSummary(),
                topics,
                document.getAiAnalyzedAt(),
                document.getTextSnippet() != null,
                aiClientService.isConfigured(),
                access.can(user, document, Permission.ANALYZE_DOCUMENT),
                DocumentAnalysisService.AUTO_ANALYZED.contains(document.getCategory()));
    }

    /** Metadata of one document, as in the list. */
    @Transactional(readOnly = true)
    public DocumentResponse get(Long id, String email) {
        User user = access.currentUser(email);
        Document document = access.authorizeDocument(user, id, Permission.VIEW_DOCUMENT);
        return toResponse(DocumentSummary.of(document), user, access.can(user, document, Permission.DELETE_DOCUMENT));
    }

    /** The document if the user may view it; 404 or 403 otherwise. Owner and course are loaded. */
    @Transactional(readOnly = true)
    public Document getViewable(Long id, String email) {
        return access.authorizeDocument(access.currentUser(email), id, Permission.VIEW_DOCUMENT);
    }

    /** Assignments the AI found in a document; visible to everyone who can view the document. */
    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> assignments(Long id, String email, Pageable pageable) {
        access.authorizeDocument(access.currentUser(email), id, Permission.VIEW_DOCUMENT);
        return PageResponse.of(assignmentRepository.findViewsBySourceDocumentId(id, pageable));
    }

    /** False when the executor's queue is full; the document then simply stays without generated assignments. */
    private boolean submitAnalysis(Long documentId, boolean manual) {
        try {
            if (manual) {
                documentAnalysisService.reanalyzeAsync(documentId);
            } else {
                documentAnalysisService.analyzeAndCreateTasksAsync(documentId);
            }
            return true;
        } catch (TaskRejectedException ex) {
            log.warn("AI analysis of document {} not queued: {}", documentId, ex.getMessage());
            return false;
        }
    }

    /** Documents of the user's courses; with {@code courseId}, of that course only (403 if it is not theirs). */
    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(String email, Long courseId, Pageable pageable) {
        User user = access.currentUser(email);
        if (courseId != null) {
            access.authorizeCourse(user, courseId, Permission.VIEW_COURSE);
        }
        Scope scope = access.scope(user);
        Page<DocumentSummary> documents;
        if (scope.allCourses()) {
            documents = documentRepository.findAllSummaries(courseId, pageable);
        } else if (scope.isEmpty()) {
            documents = Page.empty(pageable);
        } else {
            documents = documentRepository.findSummariesInCourses(scope.courseIds(), scope.includeTeacherOnly(),
                    courseId, pageable);
        }
        Set<Long> managed = managedCourseIds(user, scope);
        return PageResponse.of(documents.map(document -> toResponse(document, user, canDelete(document, user, managed))));
    }

    /** Full-text search over the extracted text, best match first, within the user's courses. */
    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> search(String query, String email, Pageable pageable) {
        String trimmed = query == null ? "" : query.strip();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("Search query must not be empty");
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            throw ApiException.badRequest("Search query must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        User user = access.currentUser(email);
        Scope scope = access.scope(user);
        if (scope.isEmpty()) {
            return PageResponse.of(Page.empty(pageable));
        }

        Page<Long> ids = documentRepository.searchVisibleIds(trimmed, scope.allCourses(), scope.courseIdsParam(),
                scope.includeTeacherOnly(), PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        // The second query loads only this page's rows; the relevance order of the ids is then restored.
        Map<Long, DocumentSummary> byId = documentRepository.findSummariesByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(DocumentSummary::id, Function.identity()));
        Set<Long> managed = managedCourseIds(user, scope);
        List<DocumentResponse> content = ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull) // Deleted between the two queries.
                .map(summary -> toResponse(summary, user, canDelete(summary, user, managed)))
                .toList();
        return PageResponse.of(new PageImpl<>(content, ids.getPageable(), ids.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public DocumentTextResponse text(Long id, String email) {
        Document document = getViewable(id, email);
        String text = document.getExtractedText();
        return new DocumentTextResponse(document.getId(), document.getOriginalFileName(),
                text != null, document.isTextTruncated(), text);
    }

    @Transactional(readOnly = true)
    public DocumentContent download(Long id, String email) {
        Document document = getViewable(id, email);
        return new DocumentContent(document, fileStorageService.load(document.getStoredFileName()));
    }

    /** Removes the document with the assignments found in it and their students' tasks. */
    @Transactional
    public void delete(Long id, String email) {
        User user = access.currentUser(email);
        Document document = access.authorizeDocument(user, id, Permission.DELETE_DOCUMENT);
        taskRepository.deleteByAssignmentSourceDocumentId(id);
        assignmentRepository.deleteBySourceDocumentId(id);
        taskRepository.deleteByDocumentId(id);
        documentRepository.delete(document);

        // Remove the file only once the row is really gone, so a rolled-back delete never loses content.
        String storedFileName = document.getStoredFileName();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.delete(storedFileName);
            }
        });
        auditService.record(user.getEmail(), AuditService.DOCUMENT_DELETED,
                "Document " + id + " \"" + document.getOriginalFileName() + "\"");
        log.info("User {} deleted document {}", user.getEmail(), id);
    }

    private ExtractedText extractText(byte[] content, String fileName, String contentType) {
        return documentParserService.extractText(new ByteArrayInputStream(content), fileName, contentType);
    }

    /** Courses whose documents the user may delete: the taught ones for a TEACHER, none for a STUDENT. */
    private static Set<Long> managedCourseIds(User user, Scope scope) {
        return user.getRole() == Role.ROLE_TEACHER ? Set.copyOf(scope.courseIds()) : Set.of();
    }

    // Mirrors AccessControlService.can(..., DELETE_DOCUMENT) for list rows, which carry no course teacher.
    private static boolean canDelete(DocumentSummary document, User user, Set<Long> managedCourseIds) {
        return user.getRole() == Role.ROLE_ADMIN
                || (document.courseId() != null && managedCourseIds.contains(document.courseId()));
    }

    private static DocumentResponse toResponse(DocumentSummary document, User viewer, boolean canDelete) {
        return new DocumentResponse(
                document.id(),
                document.originalFileName(),
                document.contentType(),
                document.fileSize(),
                document.ownerEmail(),
                document.ownerFullName(),
                document.uploadedAt(),
                canDelete,
                document.category(),
                document.visibility(),
                document.courseId(),
                document.courseTitle(),
                document.courseCode(),
                document.textSnippet() != null,
                document.textSnippet(),
                document.ownerId().equals(viewer.getId()));
    }

    // Keeps only the last path segment (browsers may send "C:\...\name.pdf") and bounds the length.
    static String sanitizeFileName(String originalFileName) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(
                originalFileName == null ? "" : originalFileName));
        name = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(name.length() - MAX_FILE_NAME_LENGTH) : name;
    }

    private static String sanitizeContentType(String contentType) {
        if (contentType == null || contentType.length() > MAX_CONTENT_TYPE_LENGTH) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            return MediaType.parseMediaType(contentType).toString();
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
