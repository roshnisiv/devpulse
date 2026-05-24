package com.devpulse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Scrapes the GitHub REST API to collect raw repository data.
 * Uses WebClient (non-blocking HTTP) for efficient API calls.
 *
 * GitHub API docs: https://docs.github.com/en/rest
 */
@Slf4j
@Service
public class GitHubApiClient {

    private final WebClient webClient;

    @Value("${alert.stale-pr.days}")
    private int stalePrDays;

    public GitHubApiClient(
            @Value("${github.api.base-url}") String baseUrl,
            @Value("${github.api.token}") String token) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    // -------------------------------------------------------
    // Pull Requests
    // -------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getOpenPullRequests(String owner, String repo) {
        log.debug("Fetching open PRs for {}/{}", owner, repo);
        return webClient.get()
                .uri("/repos/{owner}/{repo}/pulls?state=open&per_page=100", owner, repo)
                .retrieve()
                .bodyToMono(List.class)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch PRs for {}/{}: {}", owner, repo, e.getMessage());
                    return Mono.just(List.of());
                })
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMergedPullRequests(String owner, String repo, int days) {
        String since = Instant.now().minus(days, ChronoUnit.DAYS).toString();
        log.debug("Fetching merged PRs since {} for {}/{}", since, owner, repo);
        return webClient.get()
                .uri("/repos/{owner}/{repo}/pulls?state=closed&sort=updated&direction=desc&per_page=100",
                        owner, repo)
                .retrieve()
                .bodyToMono(List.class)
                .map(prs -> prs.stream()
                        .filter(pr -> {
                            Object mergedAt = ((Map<?, ?>) pr).get("merged_at");
                            return mergedAt != null &&
                                   Instant.parse(mergedAt.toString()).isAfter(Instant.parse(since));
                        })
                        .toList())
                .onErrorResume(e -> {
                    log.warn("Failed to fetch merged PRs: {}", e.getMessage());
                    return Mono.just(List.of());
                })
                .block();
    }

    // -------------------------------------------------------
    // Issues
    // -------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRepoInfo(String owner, String repo) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch repo info: {}", e.getMessage());
                    return Mono.just(Map.of());
                })
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRecentIssues(String owner, String repo, int days) {
        String since = Instant.now().minus(days, ChronoUnit.DAYS).toString();
        return webClient.get()
                .uri("/repos/{owner}/{repo}/issues?state=all&since={since}&per_page=100",
                        owner, repo, since)
                .retrieve()
                .bodyToMono(List.class)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch issues: {}", e.getMessage());
                    return Mono.just(List.of());
                })
                .block();
    }

    // -------------------------------------------------------
    // Workflows / CI
    // -------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> getWorkflowRuns(String owner, String repo, int days) {
        String since = Instant.now().minus(days, ChronoUnit.DAYS).toString();
        return webClient.get()
                .uri("/repos/{owner}/{repo}/actions/runs?created=>={since}&per_page=100",
                        owner, repo, since)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch workflow runs: {}", e.getMessage());
                    return Mono.just(Map.of("total_count", 0, "workflow_runs", List.of()));
                })
                .block();
    }

    // -------------------------------------------------------
    // Commit activity
    // -------------------------------------------------------

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCommitActivity(String owner, String repo) {
        // Returns weekly commit totals for the last year
        return webClient.get()
                .uri("/repos/{owner}/{repo}/stats/commit_activity", owner, repo)
                .retrieve()
                .bodyToMono(List.class)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch commit activity: {}", e.getMessage());
                    return Mono.just(List.of());
                })
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getContributors(String owner, String repo) {
        return webClient.get()
                .uri("/repos/{owner}/{repo}/contributors?per_page=100", owner, repo)
                .retrieve()
                .bodyToMono(List.class)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch contributors: {}", e.getMessage());
                    return Mono.just(List.of());
                })
                .block();
    }

    public int getStalePrDays() {
        return stalePrDays;
    }
}
