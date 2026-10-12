package com.university.docassistant.dto;

import com.university.docassistant.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTOs for tasks.
 */
public final class TaskDtos {

    private TaskDtos() {
    }

    public record UpdateStatusRequest(
            @NotNull TaskStatus status
    ) {
    }
}
