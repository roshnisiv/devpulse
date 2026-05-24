package com.devpulse.model;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

/**
 * Records every alert dispatched so we can audit history
 * and avoid sending duplicate alerts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "alert_events")
public class AlertEvent {

    @Id
    private String id;

    @Indexed
    private String repoFullName;

    private AlertType type;
    private String message;
    private double triggerValue;   // the metric value that crossed the threshold
    private double threshold;      // the configured threshold

    private boolean emailSent;
    private boolean slackSent;

    @Indexed
    private Instant sentAt;

    public enum AlertType {
        STALE_PR,
        LOW_HEALTH_SCORE,
        HIGH_OPEN_ISSUES,
        CI_FAILURE_SPIKE,
        CONTRIBUTOR_DROP
    }
}
