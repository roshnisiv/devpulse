package com.devpulse.service;

import com.devpulse.model.RepoMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates a delivery health score (0-100) for a repository
 * based on its collected metrics.
 *
 * Scoring breakdown:
 *   - CI success rate     : 30 points
 *   - PR cycle health     : 25 points
 *   - Issue management    : 20 points
 *   - Commit activity     : 15 points
 *   - Contributor health  : 10 points
 */
@Slf4j
@Service
public class HealthScoreEngine {

    public RepoMetrics calculateScore(RepoMetrics metrics) {
        List<String> issues = new ArrayList<>();
        double score = 0;

        // --- CI Success Rate (30 pts) ---
        double ciScore = 0;
        if (metrics.getWorkflowRunsLast7Days() == 0) {
            ciScore = 15; // no pipelines: neutral, not penalised
            issues.add("No CI runs detected in last 7 days");
        } else {
            ciScore = (metrics.getCiSuccessRatePct() / 100.0) * 30;
            if (metrics.getCiSuccessRatePct() < 80) {
                issues.add(String.format("CI success rate low: %.0f%%", metrics.getCiSuccessRatePct()));
            }
        }
        score += ciScore;

        // --- PR Cycle Health (25 pts) ---
        double prScore = 25;
        if (metrics.getStalePullRequests() > 0) {
            double penaltyPerStalePr = 3.0;
            double prPenalty = Math.min(metrics.getStalePullRequests() * penaltyPerStalePr, 20);
            prScore -= prPenalty;
            issues.add(String.format("%d stale PR(s) open > threshold days", metrics.getStalePullRequests()));
        }
        if (metrics.getAvgPrMergeTimeHours() > 72) {
            prScore -= 5;
            issues.add(String.format("Slow PR merge time: %.0fh average", metrics.getAvgPrMergeTimeHours()));
        }
        score += Math.max(prScore, 0);

        // --- Issue Management (20 pts) ---
        double issueScore = 20;
        int openIssues = metrics.getOpenIssues();
        if (openIssues > 100) {
            issueScore -= 10;
            issues.add("High open issue backlog: " + openIssues);
        } else if (openIssues > 50) {
            issueScore -= 5;
        }
        // Good sign: closing more issues than opening
        int issueVelocity = metrics.getIssuesClosedLast7Days() - metrics.getIssuesOpenedLast7Days();
        if (issueVelocity < -5) {
            issueScore -= 5;
            issues.add("Issue backlog growing: opened " + metrics.getIssuesOpenedLast7Days()
                    + ", closed " + metrics.getIssuesClosedLast7Days() + " in last 7 days");
        } else if (issueVelocity > 0) {
            issueScore += 2; // bonus for closing faster than opening
        }
        score += Math.min(Math.max(issueScore, 0), 22); // cap bonus at 22

        // --- Commit Activity (15 pts) ---
        double commitScore = 15;
        if (metrics.getCommitsLast7Days() == 0) {
            commitScore = 0;
            issues.add("No commits in the last 7 days - repo may be stale");
        } else if (metrics.getCommitsLast7Days() < 3) {
            commitScore = 7;
            issues.add("Very low commit activity: " + metrics.getCommitsLast7Days() + " commits last week");
        }
        score += commitScore;

        // --- Contributor Health (10 pts) ---
        double contribScore = 10;
        if (metrics.getUniqueContributorsLast30Days() == 1) {
            contribScore = 4;
            issues.add("Single contributor - bus factor risk");
        } else if (metrics.getUniqueContributorsLast30Days() == 0) {
            contribScore = 0;
            issues.add("No contributors detected in last 30 days");
        }
        score += contribScore;

        // Clamp to 0-100
        double finalScore = Math.min(Math.max(score, 0), 100);
        metrics.setHealthScore(finalScore);
        metrics.setHealthGrade(scoreToGrade(finalScore));
        metrics.setHealthIssues(issues);

        log.info("Health score for {}: {:.1f} ({})",
                metrics.getRepoFullName(), finalScore, metrics.getHealthGrade());

        return metrics;
    }

    private String scoreToGrade(double score) {
        if (score >= 90) return "A";
        if (score >= 75) return "B";
        if (score >= 60) return "C";
        if (score >= 40) return "D";
        return "F";
    }
}
