package com.university.docassistant.service;

import com.university.docassistant.entity.Course;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.exception.ErrorCodes;
import com.university.docassistant.repository.AssignmentRepository;
import com.university.docassistant.repository.CourseRepository;
import com.university.docassistant.repository.TaskRepository;
import com.university.docassistant.repository.TaskRepository.TaskAccess;
import com.university.docassistant.repository.TaskSubmissionRepository;
import com.university.docassistant.security.AccessControlService;
import com.university.docassistant.security.Permission;
import com.university.docassistant.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Students upload only as a submission to their own task in a course they are enrolled in. */
class SubmissionServiceTest {

    private final TaskSubmissionRepository submissions = mock(TaskSubmissionRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final StorageService storage = mock(StorageService.class);
    private final SubmissionService service = new SubmissionService(submissions, tasks,
            mock(AssignmentRepository.class), courses, access, storage, mock(AuditService.class));

    private final User student = user(1L, Role.ROLE_STUDENT, "s@uni.kz");
    private final User teacher = user(2L, Role.ROLE_TEACHER, "t@uni.kz");
    private final Course course = Course.builder().id(10L).title("Algorithms").build();
    private final List<MultipartFile> files = List.of(
            new MockMultipartFile("files", "solution.pdf", "application/pdf", new byte[]{1, 2, 3}));

    @Test
    void teacherCannotSubmitAndNothingIsRead() {
        when(access.currentUser("t@uni.kz")).thenReturn(teacher);

        assertStatus(() -> service.submit(7L, files, "t@uni.kz"), HttpStatus.FORBIDDEN);
        verify(tasks, never()).findAccessById(any());
        verify(storage, never()).store(any(byte[].class));
    }

    @Test
    void studentCannotSubmitToSomeoneElsesTask() {
        when(access.currentUser("s@uni.kz")).thenReturn(student);
        when(tasks.findAccessById(7L)).thenReturn(Optional.of(new TaskAccess(7L, 99L, 10L, 3L)));

        assertStatus(() -> service.submit(7L, files, "s@uni.kz"), HttpStatus.FORBIDDEN);
        verify(storage, never()).store(any(byte[].class));
    }

    @Test
    void studentWhoLeftTheCourseCannotSubmit() {
        when(access.currentUser("s@uni.kz")).thenReturn(student);
        when(tasks.findAccessById(7L)).thenReturn(Optional.of(new TaskAccess(7L, 1L, 10L, 3L)));
        when(courses.findById(10L)).thenReturn(Optional.of(course));
        when(access.isMember(student, course)).thenReturn(false);

        assertStatus(() -> service.submit(7L, files, "s@uni.kz"), HttpStatus.FORBIDDEN);
        verify(submissions, never()).saveAll(anyList());
    }

    @Test
    void unsupportedFileRejectsTheWholeSubmission() {
        when(access.currentUser("s@uni.kz")).thenReturn(student);
        when(tasks.findAccessById(7L)).thenReturn(Optional.of(new TaskAccess(7L, 1L, 10L, 3L)));
        when(courses.findById(10L)).thenReturn(Optional.of(course));
        when(access.isMember(student, course)).thenReturn(true);
        List<MultipartFile> withScript = List.of(files.get(0),
                new MockMultipartFile("files", "run.exe", "application/octet-stream", new byte[]{1}));

        assertStatus(() -> service.submit(7L, withScript, "s@uni.kz"), HttpStatus.BAD_REQUEST);
        verify(storage, never()).store(any(byte[].class));
    }

    @Test
    void enrolledStudentSubmitsToOwnTask() {
        when(access.currentUser("s@uni.kz")).thenReturn(student);
        when(tasks.findAccessById(7L)).thenReturn(Optional.of(new TaskAccess(7L, 1L, 10L, 3L)));
        when(courses.findById(10L)).thenReturn(Optional.of(course));
        when(access.isMember(student, course)).thenReturn(true);
        when(storage.store(any(byte[].class))).thenReturn("stored-1");
        when(submissions.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(7L, files, "s@uni.kz");

        verify(storage).store(any(byte[].class));
        verify(submissions).saveAll(anyList());
    }

    @Test
    void otherStudentCannotReadSubmissionsOfATask() {
        User other = user(3L, Role.ROLE_STUDENT, "o@uni.kz");
        when(access.currentUser("o@uni.kz")).thenReturn(other);
        when(tasks.findAccessById(7L)).thenReturn(Optional.of(new TaskAccess(7L, 1L, 10L, 3L)));
        when(access.authorizeCourse(other, 10L, Permission.MANAGE_ASSIGNMENTS))
                .thenThrow(ApiException.forbidden(ErrorCodes.COURSE_NOT_ENROLLED, "You are not enrolled in this course"));

        assertStatus(() -> service.listForTask(7L, "o@uni.kz"), HttpStatus.FORBIDDEN);
        verify(submissions, never()).findViewsByTaskId(any());
    }

    @Test
    void onlyStudentsHoldTheSubmitPermission() {
        assertThat(Permission.granted(Role.ROLE_STUDENT, Permission.SUBMIT_TASK)).isTrue();
        assertThat(Permission.granted(Role.ROLE_TEACHER, Permission.SUBMIT_TASK)).isFalse();
        assertThat(Permission.granted(Role.ROLE_ADMIN, Permission.SUBMIT_TASK)).isFalse();
        assertThat(Permission.granted(Role.ROLE_STUDENT, Permission.UPLOAD_DOCUMENT)).isFalse();
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getStatus()).isEqualTo(status));
    }

    private static User user(Long id, Role role, String email) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setEmail(email);
        return user;
    }
}
