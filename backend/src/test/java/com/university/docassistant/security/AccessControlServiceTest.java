package com.university.docassistant.security;

import com.university.docassistant.entity.Course;
import com.university.docassistant.entity.Document;
import com.university.docassistant.entity.DocumentVisibility;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.repository.CourseRepository;
import com.university.docassistant.repository.DocumentRepository;
import com.university.docassistant.repository.EnrollmentRepository;
import com.university.docassistant.repository.UserRepository;
import com.university.docassistant.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The core rule: enrolled students, the course's own teacher, and ADMIN - nobody else. */
class AccessControlServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AccessControlService access = new AccessControlService(users, courses, documents, enrollments, audit);

    private final User student = user(1L, Role.ROLE_STUDENT);
    private final User otherStudent = user(2L, Role.ROLE_STUDENT);
    private final User teacher = user(3L, Role.ROLE_TEACHER);
    private final User otherTeacher = user(4L, Role.ROLE_TEACHER);
    private final User admin = user(5L, Role.ROLE_ADMIN);
    private final Course course = Course.builder().id(10L).title("Java").teacher(teacher).build();

    private static User user(long id, Role role) {
        return User.builder().id(id).email("u" + id + "@uni.kz").role(role).build();
    }

    private Document document(long id, Course of, DocumentVisibility visibility) {
        Document document = Document.builder().id(id).course(of).visibility(visibility).owner(teacher).build();
        when(documents.findWithOwnerById(id)).thenReturn(Optional.of(document));
        return document;
    }

    @BeforeEach
    void setUp() {
        when(courses.findWithTeacherById(10L)).thenReturn(Optional.of(course));
        when(enrollments.existsByStudentIdAndCourseId(1L, 10L)).thenReturn(true);
    }

    @Test
    void enrolledStudentSeesCourseDocumentsButNotTeacherNotes() {
        document(100L, course, DocumentVisibility.COURSE_STUDENTS);
        document(101L, course, DocumentVisibility.TEACHER_ONLY);

        assertThat(access.authorizeDocument(student, 100L, Permission.VIEW_DOCUMENT).getId()).isEqualTo(100L);
        assertForbidden(() -> access.authorizeDocument(student, 101L, Permission.VIEW_DOCUMENT));
    }

    @Test
    void studentOutsideTheCourseIsForbiddenAndLogged() {
        document(100L, course, DocumentVisibility.COURSE_STUDENTS);

        assertForbidden(() -> access.authorizeDocument(otherStudent, 100L, Permission.VIEW_DOCUMENT));
        assertForbidden(() -> access.authorizeCourse(otherStudent, 10L, Permission.VIEW_COURSE));
        verify(audit).record(eq("u2@uni.kz"), eq(AuditService.ACCESS_DENIED), startsWith("VIEW_DOCUMENT on document 100"));
    }

    @Test
    void studentCannotManageEvenInOwnCourse() {
        document(100L, course, DocumentVisibility.COURSE_STUDENTS);

        assertForbidden(() -> access.authorizeDocument(student, 100L, Permission.DELETE_DOCUMENT));
        assertForbidden(() -> access.authorizeCourse(student, 10L, Permission.UPLOAD_DOCUMENT));
    }

    @Test
    void teacherManagesOnlyOwnCourses() {
        document(100L, course, DocumentVisibility.TEACHER_ONLY);

        assertThat(access.authorizeDocument(teacher, 100L, Permission.DELETE_DOCUMENT)).isNotNull();
        assertThat(access.authorizeCourse(teacher, 10L, Permission.UPLOAD_DOCUMENT)).isSameAs(course);
        assertForbidden(() -> access.authorizeDocument(otherTeacher, 100L, Permission.VIEW_DOCUMENT));
        assertForbidden(() -> access.authorizeCourse(otherTeacher, 10L, Permission.VIEW_COURSE));
    }

    @Test
    void adminHasSystemLevelAccessIncludingCourselessDocuments() {
        document(100L, course, DocumentVisibility.TEACHER_ONLY);
        document(200L, null, DocumentVisibility.PUBLIC_ALL);

        assertThat(access.authorizeDocument(admin, 100L, Permission.DELETE_DOCUMENT)).isNotNull();
        assertThat(access.authorizeDocument(admin, 200L, Permission.VIEW_DOCUMENT)).isNotNull();
        assertThat(access.authorizeCourse(admin, 10L, Permission.MANAGE_COURSE)).isSameAs(course);
    }

    @Test
    void courselessLegacyDocumentsAreAdminOnly() {
        document(200L, null, DocumentVisibility.PUBLIC_ALL);

        assertForbidden(() -> access.authorizeDocument(student, 200L, Permission.VIEW_DOCUMENT));
        assertForbidden(() -> access.authorizeDocument(teacher, 200L, Permission.VIEW_DOCUMENT));
    }

    @Test
    void missingDocumentOrCourseIs404() {
        when(documents.findWithOwnerById(999L)).thenReturn(Optional.empty());
        when(courses.findWithTeacherById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.authorizeDocument(student, 999L, Permission.VIEW_DOCUMENT))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> access.authorizeCourse(student, 999L, Permission.VIEW_COURSE))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void scopeListsAllowedCoursesPerRole() {
        when(enrollments.findCourseIdsByStudentId(1L)).thenReturn(List.of(10L, 11L));
        when(courses.findIdsByTeacherId(3L)).thenReturn(List.of(10L));
        when(courses.findAllIds()).thenReturn(List.of(10L, 11L, 12L));

        assertThat(access.scope(student)).isEqualTo(new AccessControlService.Scope(false, List.of(10L, 11L), false));
        assertThat(access.scope(teacher)).isEqualTo(new AccessControlService.Scope(false, List.of(10L), true));
        assertThat(access.scope(admin)).isEqualTo(new AccessControlService.Scope(true, List.of(10L, 11L, 12L), true));
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
