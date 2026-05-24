package com.devpulse.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;

/**
 * A snapshot of a repository's health metrics at a point in time.
 * Stored in MongoDB and used to calculate health scores and trigger alerts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "repo_metrics")
public class RepoMetrics {

    @Id
    private String id;

    @Indexed
    private String repoFullName;     // e.g. "spring-projects/spring-boot"

    private String repoOwner;
    private String repoName;

    // Pull Request metrics
    private int openPullRequests;
    private int stalePullRequests;   // PRs open > threshold days
    private double avgPrMergeTimeHours;
    private int mergedPrsLast30Days;

    // Issue metrics
    private int openIssues;
    private int issuesOpenedLast7Days;
    private int issuesClosedLast7Days;

    // CI/CD metrics
    private int workflowRunsLast7Days;
    private int failedWorkflowsLast7Days;
    private double ciSuccessRatePct;   // 0-100

    // Commit activity
    private int commitsLast7Days;
    private int uniqueContributorsLast30Days;

    // Calculated health score (0-100)
    private double healthScore;
    private String healthGrade;       // A, B, C, D, F
    private List<String> healthIssues; // Human-readable reasons for low score

    // Alert state
    private boolean alertSent;

    @Indexed
    private Instant capturedAt;
}
