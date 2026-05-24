package com.devpulse.alert;

import com.devpulse.model.AlertEvent;
import com.devpulse.model.AlertEvent.AlertType;
import com.devpulse.model.RepoMetrics;
import com.devpulse.repository.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Evaluates metrics against thresholds and dispatches alerts
 * via email and/or Slack webhooks.
 *
 * Implements cooldown logic to prevent duplicate alert spam.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertEventRepository alertEventRepository;
    private final JavaMailSender mailSender;

    @Value("${alert.health-score.min}")
    private double minHealthScore;

    @Value("${alert.stale-pr.days}")
    private int stalePrDays;

    @Value("${alert.open-issues.max}")
    private int maxOpenIssues;

    @Value("${alert.email.enabled}")
    private boolean emailEnabled;

    @Value("${alert.email.to}")
    private String emailTo;

    @Value("${alert.slack.enabled}")
    private boolean slackEnabled;

    @Value("${alert.slack.webhook-url}")
    private String slackWebhookUrl;

    // Don't re-alert the same thing within 4 hours
    private static final int COOLDOWN_HOURS = 4;

    /**
     * Evaluates all alert conditions for a repo's latest metrics snapshot.
     */
    public void evaluateAndAlert(RepoMetrics metrics) {
        checkHealthScore(metrics);
        checkStalePrs(metrics);
        checkOpenIssues(metrics);
        checkCiFailures(metrics);
    }

    private void checkHealthScore(RepoMetrics metrics) {
        if (metrics.getHealthScore() < minHealthScore) {
            String message = String.format(
                "🚨 [%s] Health score dropped to %.0f/100 (threshold: %.0f). Issues: %s",
                metrics.getRepoFullName(),
                metrics.getHealthScore(),
                minHealthScore,
                String.join(", ", metrics.getHealthIssues())
            );
            dispatch(metrics.getRepoFullName(), AlertType.LOW_HEALTH_SCORE, message,
                    metrics.getHealthScore(), minHealthScore);
        }
    }

    private void checkStalePrs(RepoMetrics metrics) {
        if (metrics.getStalePullRequests() > 0) {
            String message = String.format(
                "⏳ [%s] %d pull request(s) have been open for more than %d days without activity.",
                metrics.getRepoFullName(),
                metrics.getStalePullRequests(),
                stalePrDays
            );
            dispatch(metrics.getRepoFullName(), AlertType.STALE_PR, message,
                    metrics.getStalePullRequests(), 0);
        }
    }

    private void checkOpenIssues(RepoMetrics metrics) {
        if (metrics.getOpenIssues() > maxOpenIssues) {
            String message = String.format(
                "📋 [%s] Open issue count is high: %d (threshold: %d). Backlog may need triage.",
                metrics.getRepoFullName(),
                metrics.getOpenIssues(),
                maxOpenIssues
            );
            dispatch(metrics.getRepoFullName(), AlertType.HIGH_OPEN_ISSUES, message,
                    metrics.getOpenIssues(), maxOpenIssues);
        }
    }

    private void checkCiFailures(RepoMetrics metrics) {
        if (metrics.getWorkflowRunsLast7Days() > 0 && metrics.getCiSuccessRatePct() < 60) {
            String message = String.format(
                "❌ [%s] CI pipeline failing: %.0f%% success rate over %d runs this week.",
                metrics.getRepoFullName(),
                metrics.getCiSuccessRatePct(),
                metrics.getWorkflowRunsLast7Days()
            );
            dispatch(metrics.getRepoFullName(), AlertType.CI_FAILURE_SPIKE, message,
                    metrics.getCiSuccessRatePct(), 60);
        }
    }

    /**
     * Core dispatch method — checks cooldown, sends alert, records it.
     */
    private void dispatch(String repoFullName, AlertType type, String message,
                          double triggerValue, double threshold) {

        // Cooldown check — don't spam if we already alerted recently
        Instant cooldownSince = Instant.now().minus(COOLDOWN_HOURS, ChronoUnit.HOURS);
        if (alertEventRepository.existsByRepoFullNameAndTypeAndSentAtAfter(
                repoFullName, type, cooldownSince)) {
            log.debug("Alert suppressed (cooldown active): {} / {}", repoFullName, type);
            return;
        }

        log.warn("ALERT: {}", message);

        boolean emailSent = false;
        boolean slackSent = false;

        if (emailEnabled) {
            emailSent = sendEmail(message, repoFullName);
        }

        if (slackEnabled) {
            slackSent = sendSlack(message);
        }

        // Record the alert
        AlertEvent event = AlertEvent.builder()
                .repoFullName(repoFullName)
                .type(type)
                .message(message)
                .triggerValue(triggerValue)
                .threshold(threshold)
                .emailSent(emailSent)
                .slackSent(slackSent)
                .sentAt(Instant.now())
                .build();

        alertEventRepository.save(event);
    }

    private boolean sendEmail(String message, String repoFullName) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(emailTo);
            mail.setSubject("DevPulse Alert: " + repoFullName);
            mail.setText(message + "\n\nThis alert was generated by DevPulse.");
            mailSender.send(mail);
            log.info("Email alert sent to {}", emailTo);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email alert: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendSlack(String message) {
        try {
            WebClient.create()
                    .post()
                    .uri(slackWebhookUrl)
                    .bodyValue(Map.of("text", message))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Slack alert sent");
            return true;
        } catch (Exception e) {
            log.error("Failed to send Slack alert: {}", e.getMessage());
            return false;
        }
    }
}
