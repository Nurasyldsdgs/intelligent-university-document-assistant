package com.university.docassistant.service;

import com.university.docassistant.entity.Assignment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Hooks for things students should be told about. Empty for now: Sprint 4 sends the notifications from here.
 * Called inside the transaction that made the change.
 */
@Component
public class AssignmentEvents {

    /** The deadline of an ACTIVE assignment changed; {@code previous} or {@code current} may be null (no deadline). */
    public void deadlineChanged(Assignment assignment, LocalDateTime previous, LocalDateTime current) {
        // Sprint 4: notify the course's students.
    }
}
