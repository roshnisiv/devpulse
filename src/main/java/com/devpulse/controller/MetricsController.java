package com.devpulse.controller;

import com.devpulse.alert.AlertService;
import com.devpulse.model.AlertEvent;
import com.devpulse.model.RepoMetrics;
import com.devpulse.repository.AlertEventRepository;
import com.devpulse.service.MetricsCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * REST API for DevPulse.
 *
 * Endpoints:
 *   GET  /api/health                        → service health check
 *   GET  /api/repos                         → latest metrics for all watched repos
 *   GET  /api/repos/{owner}/{repo}          → latest metrics for one repo
 *   GET  /api/repos/{owner}/{repo}/history  → historical snapshots
 *   POST /api/repos/{owner}/{repo}/refresh  → trigger manual scrape
 *   GET  /api/alerts                        → recent alert history
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // Allow the frontend dashboard to call us
public class MetricsController {

    private final MetricsCollectionService collectionService;
    private final AlertEventRepository alertEventRepository;

    // -------------------------------------------------------
    // Health Check
    // -------------------------------------------------------

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "DevPulse",
            "timestamp", Instant.now()
        ));
    }

    // -------------------------------------------------------
    // Repo Metrics
    // -------------------------------------------------------

    @GetMapping("/repos")
    public ResponseEntity<List<RepoMetrics>> getAllRepos() {
        return ResponseEntity.ok(collectionService.getAllLatest());
    }

    @GetMapping("/repos/{owner}/{repo}")
    public ResponseEntity<RepoMetrics> getRepo(
            @PathVariable String owner,
            @PathVariable String repo) {

        return collectionService.getLatest(owner + "/" + repo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/repos/{owner}/{repo}/history")
    public ResponseEntity<List<RepoMetrics>> getHistory(
            @PathVariable String owner,
            @PathVariable String repo) {

        List<RepoMetrics> history = collectionService.getHistory(owner + "/" + repo);
        if (history.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(history);
    }

    /**
     * Manually trigger a scrape for a single repo.
     * Useful for demos and testing without waiting for the scheduler.
     */
    @PostMapping("/repos/{owner}/{repo}/refresh")
    public ResponseEntity<RepoMetrics> refreshRepo(
            @PathVariable String owner,
            @PathVariable String repo) {

        RepoMetrics metrics = collectionService.collectRepo(owner, repo);
        return ResponseEntity.ok(metrics);
    }

    // -------------------------------------------------------
    // Alert History
    // -------------------------------------------------------

    @GetMapping("/alerts")
    public ResponseEntity<List<AlertEvent>> getRecentAlerts(
            @RequestParam(defaultValue = "24") int hoursBack) {

        Instant since = Instant.now().minus(hoursBack, ChronoUnit.HOURS);
        return ResponseEntity.ok(alertEventRepository.findBySentAtAfterOrderBySentAtDesc(since));
    }

    @GetMapping("/alerts/{owner}/{repo}")
    public ResponseEntity<List<AlertEvent>> getAlertsForRepo(
            @PathVariable String owner,
            @PathVariable String repo) {

        return ResponseEntity.ok(
            alertEventRepository.findByRepoFullNameOrderBySentAtDesc(owner + "/" + repo)
        );
    }
}
