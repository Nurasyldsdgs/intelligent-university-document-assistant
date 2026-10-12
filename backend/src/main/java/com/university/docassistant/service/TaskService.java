package com.university.docassistant.service;

import com.university.docassistant.dto.PageResponse;
import com.university.docassistant.dto.TaskResponse;
import com.university.docassistant.entity.TaskStatus;
import com.university.docassistant.entity.User;
import com.university.docassistant.exception.ApiException;
import com.university.docassistant.repository.TaskRepository;
import com.university.docassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A student's own tasks (copies of their courses' assignments): listing them, the upcoming deadlines, and moving
 * them between statuses. Only the task's user can change it.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listMine(String email, TaskStatus status, Pageable pageable) {
        User user = currentUser(email);
        return PageResponse.of(status == null
                ? taskRepository.findViewsByUserId(user.getId(), pageable)
                : taskRepository.findViewsByUserIdAndStatus(user.getId(), status, pageable));
    }

    /** Unfinished tasks with a deadline from now on, nearest first. */
    @Transactional(readOnly = true)
    public List<TaskResponse> upcoming(String email, int limit) {
        User user = currentUser(email);
        return taskRepository.findUpcomingViews(user.getId(), LocalDateTime.now(), PageRequest.of(0, limit))
                .getContent();
    }

    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatus status, String email) {
        User user = currentUser(email);
        Long ownerId = taskRepository.findUserIdById(id)
                .orElseThrow(() -> ApiException.notFound("Task not found"));
        if (!ownerId.equals(user.getId())) {
            throw ApiException.forbidden("You can change only your own tasks");
        }
        taskRepository.updateStatus(id, status);
        return taskRepository.findViewById(id)
                .orElseThrow(() -> ApiException.notFound("Task not found"));
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
    }
}
