package com.university.docassistant.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-off, idempotent migration of users.role to ROLE_STUDENT/ROLE_TEACHER/ROLE_ADMIN, from both the original
 * STUDENT/TEACHER/ADMIN names and the interim ROLE_FACULTY.
 * ddl-auto=update never alters existing constraints, so Hibernate's enum check constraint is replaced here too;
 * without it, saving a user with a new role value would fail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");

        int updated = jdbcTemplate.update("""
                UPDATE users SET role = CASE role
                    WHEN 'STUDENT' THEN 'ROLE_STUDENT'
                    WHEN 'TEACHER' THEN 'ROLE_TEACHER'
                    WHEN 'ROLE_FACULTY' THEN 'ROLE_TEACHER'
                    WHEN 'ADMIN' THEN 'ROLE_ADMIN'
                END
                WHERE role IN ('STUDENT', 'TEACHER', 'ROLE_FACULTY', 'ADMIN')
                """);
        if (updated > 0) {
            log.info("Migrated {} user role(s) to ROLE_* names", updated);
        }

        jdbcTemplate.execute("""
                ALTER TABLE users ADD CONSTRAINT users_role_check
                    CHECK (role IN ('ROLE_STUDENT', 'ROLE_TEACHER', 'ROLE_ADMIN'))
                """);
    }
}
