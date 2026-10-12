package com.university.docassistant.service;

import com.university.docassistant.dto.CourseDtos.CourseDetailsResponse;
import com.university.docassistant.dto.CourseDtos.CourseResponse;
import com.university.docassistant.dto.CourseDtos.CreateCourseRequest;
import com.university.docassistant.dto.CourseDtos.StudentResponse;
import com.university.docassistant.dto.PageResponse;
import com.university.docassistant.entity.Course;
import com.university.docassistant.entity.Role;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.repository.CourseRepository;
import com.university.docassistant.repository.EnrollmentRepository;
import com.university.docassistant.repository.TaskRepository;
import com.university.docassistant.repository.UserRepository;
import com.university.docassistant.security.AccessControlService;
import com.university.docassistant.security.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * "My courses": the enrolled ones for a STUDENT, the taught ones for a TEACHER, all of them for ADMIN.
 * A course page is open to its members only (403 otherwise). Enrollment: a STUDENT can enroll themselves through
 * the course's invitation link; the course's teacher or ADMIN can enroll or remove any student. Enrolling hands the
 * course's assignments out to the student as tasks; leaving removes them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AssignmentService assignmentService;
    private final AccessControlService access;
    private final AuditService auditService;

    @Transactional
    public CourseResponse create(CreateCourseRequest request, String email) {
        User user = access.currentUser(email);
        if (!Permission.granted(user.getRole(), Permission.MANAGE_COURSE)) {
            throw ApiException.forbidden("Only TEACHER or ADMIN can create courses");
        }
        User teacher = user;
        if (request.teacherId() != null && !request.teacherId().equals(user.getId())) {
            if (user.getRole() != Role.ROLE_ADMIN) {
                throw ApiException.forbidden("Only ADMIN can create a course for another teacher");
            }
            teacher = requireTeacher(request.teacherId());
        }
        Course course = courseRepository.save(Course.builder()
                .title(request.title().trim())
                .code(StringUtils.hasText(request.code()) ? request.code().trim().toUpperCase() : null)
                .description(request.description())
                .teacher(teacher)
                .build());
        auditService.record(user.getEmail(), AuditService.COURSE_CREATED,
                "Course " + course.getId() + " \"" + course.getTitle() + "\" taught by " + teacher.getEmail());
        log.info("User {} created course {} ({}) taught by {}", user.getEmail(), course.getId(), course.getCode(),
                teacher.getEmail());
        return toResponse(course);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> listMine(String email, Pageable pageable) {
        User user = access.currentUser(email);
        Page<Course> courses = switch (user.getRole()) {
            case ROLE_ADMIN -> courseRepository.findAllWithTeacher(pageable);
            case ROLE_TEACHER -> courseRepository.findByTeacherId(user.getId(), pageable);
            case ROLE_STUDENT -> courseRepository.findByStudentId(user.getId(), pageable);
        };
        return PageResponse.of(courses.map(CourseService::toResponse));
    }

    /** The course page; 403 for a user who neither teaches it nor is enrolled in it (ADMIN always may). */
    @Transactional(readOnly = true)
    public CourseDetailsResponse details(Long courseId, String email) {
        User user = access.currentUser(email);
        Course course = access.authorizeCourse(user, courseId, Permission.VIEW_COURSE);
        return new CourseDetailsResponse(
                toResponse(course),
                enrollmentRepository.countByCourseId(courseId),
                user.getRole() == Role.ROLE_STUDENT,
                Permission.granted(user.getRole(), Permission.MANAGE_COURSE));
    }

    /** The course's students; for its teacher and ADMIN. */
    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> students(Long courseId, String email, Pageable pageable) {
        access.authorizeCourse(access.currentUser(email), courseId, Permission.MANAGE_COURSE);
        return PageResponse.of(enrollmentRepository.findStudentsByCourseId(courseId, pageable)
                .map(student -> new StudentResponse(student.getId(), student.getFullName(), student.getEmail())));
    }

    @Transactional
    public void enroll(Long courseId, Long studentId, String email) {
        User user = access.currentUser(email);
        Course course = courseRepository.findWithTeacherById(courseId)
                .orElseThrow(() -> ApiException.notFound("Course not found"));

        User student;
        if (studentId == null || studentId.equals(user.getId())) {
            // Self-enrollment through the invitation link the teacher shares.
            if (user.getRole() != Role.ROLE_STUDENT) {
                throw ApiException.badRequest("Only students can enroll in a course");
            }
            student = user;
        } else {
            access.authorizeCourse(user, course.getId(), Permission.MANAGE_COURSE);
            student = userRepository.findById(studentId)
                    .orElseThrow(() -> ApiException.notFound("Student not found"));
            if (student.getRole() != Role.ROLE_STUDENT) {
                throw ApiException.badRequest("Only students can be enrolled in a course");
            }
        }

        if (enrollmentRepository.enroll(course.getId(), student.getId()) > 0) {
            int tasks = assignmentService.handOut(course.getId());
            auditService.record(user.getEmail(), AuditService.STUDENT_ENROLLED,
                    student.getEmail() + " in course " + courseId);
            log.info("User {} enrolled {} in course {} ({} task(s) handed out)",
                    user.getEmail(), student.getEmail(), courseId, tasks);
        }
    }

    /** Removes a student from the course with their tasks of it; for the student themselves, the teacher or ADMIN. */
    @Transactional
    public void unenroll(Long courseId, Long studentId, String email) {
        User user = access.currentUser(email);
        if (!studentId.equals(user.getId())) {
            access.authorizeCourse(user, courseId, Permission.MANAGE_COURSE);
        } else if (!courseRepository.existsById(courseId)) {
            throw ApiException.notFound("Course not found");
        }
        if (enrollmentRepository.unenroll(courseId, studentId) > 0) {
            taskRepository.deleteByCourseIdAndUserId(courseId, studentId);
            auditService.record(user.getEmail(), AuditService.STUDENT_UNENROLLED,
                    "Student " + studentId + " from course " + courseId);
            log.info("User {} removed student {} from course {}", user.getEmail(), studentId, courseId);
        }
    }

    /** ADMIN hands a course over to another teacher. */
    @Transactional
    public CourseResponse assignTeacher(Long courseId, Long teacherId, String email) {
        User admin = access.currentUser(email);
        if (!Permission.granted(admin.getRole(), Permission.MANAGE_SYSTEM)) {
            throw ApiException.forbidden("Only ADMIN can change the teacher of a course");
        }
        Course course = courseRepository.findWithTeacherById(courseId)
                .orElseThrow(() -> ApiException.notFound("Course not found"));
        course.setTeacher(requireTeacher(teacherId));
        auditService.record(admin.getEmail(), AuditService.COURSE_TEACHER_CHANGED,
                "Course " + courseId + " now taught by " + course.getTeacher().getEmail());
        log.info("Admin {} assigned teacher {} to course {}", admin.getEmail(), teacherId, courseId);
        return toResponse(course);
    }

    private User requireTeacher(Long teacherId) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> ApiException.notFound("Teacher not found"));
        if (teacher.getRole() != Role.ROLE_TEACHER) {
            throw ApiException.badRequest("The user is not a TEACHER");
        }
        return teacher;
    }

    private static CourseResponse toResponse(Course course) {
        User teacher = course.getTeacher();
        return new CourseResponse(
                course.getId(),
                course.getTitle(),
                course.getCode(),
                course.getDescription(),
                teacher != null ? teacher.getId() : null,
                teacher != null ? teacher.getFullName() : null,
                teacher != null ? teacher.getEmail() : null);
    }
}
