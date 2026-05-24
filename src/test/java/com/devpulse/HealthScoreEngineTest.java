package com.devpulse;

import com.devpulse.model.RepoMetrics;
import com.devpulse.service.HealthScoreEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the health score calculation engine.
 * These run without any Spring context (fast, isolated).
 */
class HealthScoreEngineTest {

    private HealthScoreEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HealthScoreEngine();
    }

    @Test
    void perfectRepo_scoresHighly() {
        RepoMetrics metrics = healthyRepo();
        RepoMetrics result = engine.calculateScore(metrics);

        assertThat(result.getHealthScore()).isGreaterThanOrEqualTo(85.0);
        assertThat(result.getHealthGrade()).isIn("A", "B");
        assertThat(result.getHealthIssues()).isEmpty();
    }

    @Test
    void repoWithStalePrs_ispenalised() {
        RepoMetrics metrics = healthyRepo();
        metrics.setStalePullRequests(3);

        RepoMetrics result = engine.calculateScore(metrics);

        assertThat(result.getHealthScore()).isLessThan(healthyRepo_score());
        assertThat(result.getHealthIssues()).anyMatch(i -> i.contains("stale PR"));
    }

    @Test
    void repoWithNoCommits_isDrastic() {
        RepoMetrics metrics = healthyRepo();
        metrics.setCommitsLast7Days(0);

        RepoMetrics result = engine.calculateScore(metrics);

        assertThat(result.getHealthIssues()).anyMatch(i -> i.contains("No commits"));
    }

    @Test
    void repoWithLowCi_getsWarning() {
        RepoMetrics metrics = healthyRepo();
        metrics.setWorkflowRunsLast7Days(20);
        metrics.setFailedWorkflowsLast7Days(10);
        metrics.setCiSuccessRatePct(50.0);

        RepoMetrics result = engine.calculateScore(metrics);

        assertThat(result.getHealthIssues()).anyMatch(i -> i.contains("CI success rate low"));
    }

    @Test
    void singleContributor_flagsBusFactor() {
        RepoMetrics metrics = healthyRepo();
        metrics.setUniqueContributorsLast30Days(1);

        RepoMetrics result = engine.calculateScore(metrics);

        assertThat(result.getHealthIssues()).anyMatch(i -> i.contains("bus factor"));
    }

    @Test
    void scoreIsAlwaysBetween0And100() {
        RepoMetrics worst = RepoMetrics.builder()
                .repoFullName("test/repo")
                .stalePullRequests(20)
                .openIssues(200)
                .ciSuccessRatePct(0)
                .workflowRunsLast7Days(10)
                .commitsLast7Days(0)
                .uniqueContributorsLast30Days(0)
                .issuesOpenedLast7Days(20)
                .issuesClosedLast7Days(0)
                .capturedAt(Instant.now())
                .build();

        RepoMetrics result = engine.calculateScore(worst);
        assertThat(result.getHealthScore()).isBetween(0.0, 100.0);
    }

    // --- Helpers ---

    private RepoMetrics healthyRepo() {
        return RepoMetrics.builder()
                .repoFullName("test/good-repo")
                .stalePullRequests(0)
                .openPullRequests(2)
                .avgPrMergeTimeHours(24)
                .openIssues(10)
                .issuesOpenedLast7Days(3)
                .issuesClosedLast7Days(5)
                .workflowRunsLast7Days(20)
                .failedWorkflowsLast7Days(1)
                .ciSuccessRatePct(95)
                .commitsLast7Days(15)
                .uniqueContributorsLast30Days(4)
                .capturedAt(Instant.now())
                .build();
    }

    private double healthyRepo_score() {
        return engine.calculateScore(healthyRepo()).getHealthScore();
    }
}
