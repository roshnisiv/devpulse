package com.devpulse.repository;

import com.devpulse.model.AlertEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AlertEventRepository extends MongoRepository<AlertEvent, String> {

    List<AlertEvent> findByRepoFullNameOrderBySentAtDesc(String repoFullName);

    // Check if we already alerted for this repo+type in a time window
    // (prevents duplicate alert storms)
    boolean existsByRepoFullNameAndTypeAndSentAtAfter(
        String repoFullName, AlertEvent.AlertType type, Instant since
    );

    List<AlertEvent> findBySentAtAfterOrderBySentAtDesc(Instant since);
}
