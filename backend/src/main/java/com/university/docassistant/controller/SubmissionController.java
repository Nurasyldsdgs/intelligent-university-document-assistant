package com.university.docassistant.controller;

import com.university.docassistant.dto.SubmissionDtos.SubmissionResponse;
import com.university.docassistant.repository.TaskSubmissionRepository.SubmissionAccess;
import com.university.docassistant.service.SubmissionService;
import com.university.docassistant.service.SubmissionService.SubmissionContent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Students' submissions for their tasks: the only way a STUDENT uploads files. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /** Hands in one or more files for the current student's own task. */
    @PostMapping(value = "/tasks/{taskId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<SubmissionResponse>> submit(@PathVariable Long taskId,
                                                           @RequestParam("files") List<MultipartFile> files,
                                                           @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissionService.submit(taskId, files, principal.getUsername()));
    }

    @GetMapping("/tasks/{taskId}/submissions")
    public List<SubmissionResponse> forTask(@PathVariable Long taskId,
                                            @AuthenticationPrincipal UserDetails principal) {
        return submissionService.listForTask(taskId, principal.getUsername());
    }

    /** All students' submissions for an assignment; for the course's teacher or ADMIN. */
    @GetMapping("/assignments/{assignmentId}/submissions")
    public List<SubmissionResponse> forAssignment(@PathVariable Long assignmentId,
                                                  @AuthenticationPrincipal UserDetails principal) {
        return submissionService.listForAssignment(assignmentId, principal.getUsername());
    }

    @GetMapping("/submissions/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        SubmissionContent result = submissionService.download(id, principal.getUsername());
        SubmissionAccess submission = result.submission();
        // Always an attachment: student-supplied content must never render inline on our origin.
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(submission.originalFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(submission.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(result.content());
    }
}
