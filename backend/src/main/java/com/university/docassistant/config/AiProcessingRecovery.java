package com.university.docassistant.config;

import com.university.docassistant.service.AiClientService;
import com.university.docassistant.service.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resumes background AI classification after a restart. Documents left PROCESSING were interrupted mid-job and go
 * back to PENDING; then as many PENDING documents as the AI queue has free room for are queued, oldest first. The
 * rest stay PENDING for the next start (or a reclassify). Skipped entirely when the AI is not configured.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AiProcessingRecovery implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final AiClientService aiClient;
    private final DocumentProcessingService processingService;
    private final ThreadPoolTaskExecutor aiExecutor;

    public AiProcessingRecovery(JdbcTemplate jdbcTemplate, AiClientService aiClient,
                                DocumentProcessingService processingService,
                                @Qualifier(AsyncConfig.AI_EXECUTOR) ThreadPoolTaskExecutor aiExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiClient = aiClient;
        this.processingService = processingService;
        this.aiExecutor = aiExecutor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!aiClient.isConfigured()) {
            return;
        }
        int interrupted = jdbcTemplate.update("UPDATE documents SET ai_status = 'PENDING' WHERE ai_status = 'PROCESSING'");
        if (interrupted > 0) {
            log.info("{} document(s) were being classified when the server stopped; back to PENDING", interrupted);
        }

        int room = aiExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
        if (room <= 0) {
            return;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM documents WHERE ai_status = 'PENDING' ORDER BY id LIMIT ?", Long.class, room);
        int queued = 0;
        for (Long id : ids) {
            try {
                processingService.processAsync(id);
                queued++;
            } catch (TaskRejectedException ex) {
                break; // The queue filled up meanwhile; the rest wait for the next start.
            }
        }
        if (queued > 0) {
            Integer left = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM documents WHERE ai_status = 'PENDING'", Integer.class);
            log.info("Queued {} PENDING document(s) for AI classification ({} still waiting overall)", queued,
                    left == null ? 0 : Math.max(0, left - queued));
        }
    }
}
