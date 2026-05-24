package com.devpulse.service;

import com.devpulse.model.RepoMetrics;
import com.devpulse.repository.RepoMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Orchestrates collecting metrics from GitHub for each watched repo
 * and persisting them to MongoDB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsCollectionService {

    private final GitHubApiClient gitHubApiClient;
    private final HealthScoreEngine healthScoreEngine;
    private final RepoMetricsRepository metricsRepository;

    @Value("${github.repos.watch}")
    private String watchedReposConfig;

    /**
     * Collects and stores metrics for all watched repositories.
     * Called by the scheduler every 15 minutes.
     */
    public List<RepoMetrics> collectAllRepos() {
        List<String> repos = Arrays.asList(watchedReposConfig.split(","));
        List<RepoMetrics> results = new ArrayList<>();

        for (String repoFullName : repos) {
            String[] parts = repoFullName.trim().split("/");
            if (parts.length != 2) {
                log.warn("Invalid repo format (expected owner/repo): {}", repoFullName);
                continue;
            }
            try {
                RepoMetrics metrics = collectRepo(parts[0], parts[1]);
                results.add(metrics);
            } catch (Exception e) {
                log.error("Failed to collect metrics for {}: {}", repoFullName, e.getMessage(), e);
            }
        }

        log.info("Collected metrics for {} repositories", results.size());
        return results;
    }

    /**
     * Collects, scores, and stores metrics for a single repository.
     */
    public RepoMetrics collectRepo(String owner, String repo) {
        log.info("Collecting metrics for {}/{}", owner, repo);
        Instant now = Instant.now();

        // --- Fetch raw data from GitHub ---
        Map<String, Object> repoInfo = gitHubApiClient.getRepoInfo(owner, repo);

        List<Map<String, Object>> openPrs = gitHubApiClient.getOpenPullRequests(owner, repo);
        List<Map<String, Object>> mergedPrs = gitHubApiClient.getMergedPullRequests(owner, repo, 30);

        List<Map<String, Object>> recentIssues = gitHubApiClient.getRecentIssues(owner, repo, 7);
        Map<String, Object> workflowData = gitHubApiClient.getWorkflowRuns(owner, repo, 7);
        List<Map<String, Object>> commitActivity = gitHubApiClient.getCommitActivity(owner, repo);
        List<Map<String, Object>> contributors = gitHubApiClient.getContributors(owner, repo);

        // --- Calculate PR metrics ---
        int staleDays = gitHubApiClient.getStalePrDays();
        Instant staleThreshold = now.minus(staleDays, ChronoUnit.DAYS);

        long stalePrCount = openPrs.stream()
                .filter(pr -> {
                    Object createdAt = pr.get("created_at");
                    return createdAt != null &&
                           Instant.parse(createdAt.toString()).isBefore(staleThreshold);
                })
                .count();

        double avgMergeTime = mergedPrs.stream()
                .mapToDouble(pr -> {
                    Object createdAt = pr.get("created_at");
                    Object mergedAt = pr.get("merged_at");
                    if (createdAt == null || mergedAt == null) return 0;
                    return ChronoUnit.HOURS.between(
                            Instant.parse(createdAt.toString()),
                            Instant.parse(mergedAt.toString())
                    );
                })
                .average()
                .orElse(0);

        // --- Issue metrics ---
        long openedLast7 = recentIssues.stream()
                .filter(i -> i.get("pull_request") == null) // exclude PRs from issue list
                .filter(i -> "open".equals(i.get("state")))
                .count();

        long closedLast7 = recentIssues.stream()
                .filter(i -> i.get("pull_request") == null)
                .filter(i -> "closed".equals(i.get("state")))
                .count();

        int totalOpenIssues = (int) repoInfo.getOrDefault("open_issues_count", 0);

        // --- CI/CD metrics ---
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>)
                workflowData.getOrDefault("workflow_runs", List.of());

        long totalRuns = runs.size();
        long successRuns = runs.stream()
                .filter(r -> "success".equals(r.get("conclusion")))
                .count();
        double ciSuccessRate = totalRuns > 0 ? (successRuns * 100.0 / totalRuns) : 0;
        long failedRuns = totalRuns - successRuns;

        // --- Commit activity (last 7 days = last entry in weekly stats) ---
        int commitsLast7 = 0;
        if (!commitActivity.isEmpty()) {
            Map<String, Object> lastWeek = commitActivity.get(commitActivity.size() - 1);
            commitsLast7 = (int) lastWeek.getOrDefault("total", 0);
        }

        // --- Build the metrics object ---
        RepoMetrics metrics = RepoMetrics.builder()
                .repoFullName(owner + "/" + repo)
                .repoOwner(owner)
                .repoName(repo)
                .openPullRequests(openPrs.size())
                .stalePullRequests((int) stalePrCount)
                .avgPrMergeTimeHours(avgMergeTime)
                .mergedPrsLast30Days(mergedPrs.size())
                .openIssues(totalOpenIssues)
                .issuesOpenedLast7Days((int) openedLast7)
                .issuesClosedLast7Days((int) closedLast7)
                .workflowRunsLast7Days((int) totalRuns)
                .failedWorkflowsLast7Days((int) failedRuns)
                .ciSuccessRatePct(ciSuccessRate)
                .commitsLast7Days(commitsLast7)
                .uniqueContributorsLast30Days(contributors.size())
                .alertSent(false)
                .capturedAt(now)
                .build();

        // --- Score it ---
        metrics = healthScoreEngine.calculateScore(metrics);

        // --- Persist to MongoDB ---
        return metricsRepository.save(metrics);
    }

    public Optional<RepoMetrics> getLatest(String repoFullName) {
        return metricsRepository.findFirstByRepoFullNameOrderByCapturedAtDesc(repoFullName);
    }

    public List<RepoMetrics> getHistory(String repoFullName) {
        return metricsRepository.findByRepoFullNameOrderByCapturedAtDesc(repoFullName);
    }

    public List<RepoMetrics> getAllLatest() {
        return metricsRepository.findLatestForAllRepos();
    }
}
