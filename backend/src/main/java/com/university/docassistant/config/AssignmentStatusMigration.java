package com.university.docassistant.config;

import com.university.docassistant.entity.AssignmentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Idempotent: rebuilds the check constraint of assignments.status from {@link AssignmentStatus}. ddl-auto=update never
 * alters the enum check constraint Hibernate created with the table, so without this a database created before DRAFT
 * existed would reject every AI draft. Existing rows keep their status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssignmentStatusMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String values = Arrays.stream(AssignmentStatus.values())
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE assignments DROP CONSTRAINT IF EXISTS assignments_status_check");
        jdbcTemplate.execute("ALTER TABLE assignments ADD CONSTRAINT assignments_status_check CHECK (status IN ("
                + values + "))");
        log.debug("assignments.status accepts {}", values);
    }
}
