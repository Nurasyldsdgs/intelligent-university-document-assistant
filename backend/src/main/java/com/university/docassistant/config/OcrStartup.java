package com.university.docassistant.config;

import com.university.docassistant.service.DocumentProcessingService;
import com.university.docassistant.service.OcrEngine;
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
 * US-07 at startup: checks Tesseract and its languages once (OcrEngine logs one WARN listing what is missing and
 * OCR stays off), then queues the scans still waiting for OCR - an interrupted run goes back to PENDING. Documents
 * waiting for classification are left to AiProcessingRecovery: their job runs OCR first. After the masking backfill.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class OcrStartup implements ApplicationRunner {

    private final OcrEngine engine;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentProcessingService processingService;
    private final ThreadPoolTaskExecutor aiExecutor;

    public OcrStartup(OcrEngine engine, JdbcTemplate jdbcTemplate, DocumentProcessingService processingService,
                      @Qualifier(AsyncConfig.AI_EXECUTOR) ThreadPoolTaskExecutor aiExecutor) {
        this.engine = engine;
        this.jdbcTemplate = jdbcTemplate;
        this.processingService = processingService;
        this.aiExecutor = aiExecutor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!engine.isAvailable()) {
            return;
        }
        int interrupted = jdbcTemplate.update("UPDATE documents SET ocr_status = 'PENDING' WHERE ocr_status = 'RUNNING'");
        if (interrupted > 0) {
            log.info("{} document(s) were being recognized when the server stopped; back to PENDING", interrupted);
        }
        int room = aiExecutor.getThreadPoolExecutor().getQueue().remainingCapacity();
        if (room <= 0) {
            return;
        }
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM documents
                WHERE ocr_status = 'PENDING' AND ai_status NOT IN ('PENDING', 'PROCESSING')
                ORDER BY id LIMIT ?
                """, Long.class, room);
        int queued = 0;
        for (Long id : ids) {
            try {
                processingService.ocrAsync(id);
                queued++;
            } catch (TaskRejectedException ex) {
                break; // The queue filled up; the rest wait for the next start.
            }
        }
        if (queued > 0) {
            log.info("Queued {} scanned document(s) for OCR", queued);
        }
    }
}
