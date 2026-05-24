package com.devpulse.scheduler;

import com.devpulse.alert.AlertService;
import com.devpulse.model.RepoMetrics;
import com.devpulse.service.MetricsCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Triggers the metrics collection and alerting pipeline on a schedule.
 *
 * The cron expression is configurable via SCRAPER_CRON env var.
 * Default: every 15 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsScheduler {

    private final MetricsCollectionService collectionService;
    private final AlertService alertService;

    @Scheduled(cron = "${scraper.cron}")
    public void runPipeline() {
        log.info("=== DevPulse pipeline starting ===");
        long start = System.currentTimeMillis();

        List<RepoMetrics> results = collectionService.collectAllRepos();

        for (RepoMetrics metrics : results) {
            alertService.evaluateAndAlert(metrics);
        }

        long duration = System.currentTimeMillis() - start;
        log.info("=== Pipeline complete: {} repos in {}ms ===", results.size(), duration);
    }
}
