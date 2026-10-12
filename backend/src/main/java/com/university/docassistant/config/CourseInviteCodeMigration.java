package com.university.docassistant.config;

import com.university.docassistant.util.InviteCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gives every course an invitation code and makes courses.invite_code NOT NULL and unique. ddl-auto=update can only
 * add the column as nullable to a table that already has rows, so courses from before invitation codes get one here.
 * Idempotent: once every course has a code, only the (no-op) constraint statements run.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CourseInviteCodeMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Long> withoutCode = jdbcTemplate.queryForList(
                "SELECT id FROM courses WHERE invite_code IS NULL", Long.class);
        for (Long id : withoutCode) {
            jdbcTemplate.update("UPDATE courses SET invite_code = ? WHERE id = ?", InviteCodes.generate(), id);
        }
        if (!withoutCode.isEmpty()) {
            log.info("Generated invitation codes for {} existing course(s)", withoutCode.size());
        }
        jdbcTemplate.execute("ALTER TABLE courses ALTER COLUMN invite_code SET NOT NULL");
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_courses_invite_code ON courses (invite_code)");
    }
}
