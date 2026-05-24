package com.devpulse.repository;

import com.devpulse.model.RepoMetrics;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepoMetricsRepository extends MongoRepository<RepoMetrics, String> {

    // Latest snapshot for a repo
    Optional<RepoMetrics> findFirstByRepoFullNameOrderByCapturedAtDesc(String repoFullName);

    // All snapshots for a repo (for trend analysis)
    List<RepoMetrics> findByRepoFullNameOrderByCapturedAtDesc(String repoFullName);

    // Snapshots in a time window
    List<RepoMetrics> findByRepoFullNameAndCapturedAtBetween(
        String repoFullName, Instant from, Instant to
    );

    // Repos with low health scores
    @Query("{ 'healthScore': { $lt: ?0 }, 'capturedAt': { $gte: ?1 } }")
    List<RepoMetrics> findUnhealthyRepos(double minScore, Instant since);

    // Latest snapshot for every watched repo
    @Query(value = "{}", sort = "{ 'capturedAt': -1 }")
    List<RepoMetrics> findLatestForAllRepos();
}
