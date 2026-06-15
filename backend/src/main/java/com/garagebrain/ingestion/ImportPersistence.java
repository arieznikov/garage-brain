package com.garagebrain.ingestion;

import com.garagebrain.analytics.TrendEngine;
import com.garagebrain.analytics.TripSegmenter;
import com.garagebrain.persistence.ImportJobRepository;
import com.garagebrain.persistence.SessionAggregateRepository;
import com.garagebrain.persistence.SessionAggregateView;
import com.garagebrain.persistence.SessionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ImportPersistence {

    private final ImportJobRepository importJobRepository;
    private final SessionRepository sessionRepository;
    private final SessionAggregateRepository sessionAggregateRepository;
    private final TripSegmenter tripSegmenter;
    private final TrendEngine trendEngine;

    ImportPersistence(
            ImportJobRepository importJobRepository,
            SessionRepository sessionRepository,
            SessionAggregateRepository sessionAggregateRepository,
            TripSegmenter tripSegmenter,
            TrendEngine trendEngine) {
        this.importJobRepository = importJobRepository;
        this.sessionRepository = sessionRepository;
        this.sessionAggregateRepository = sessionAggregateRepository;
        this.tripSegmenter = tripSegmenter;
        this.trendEngine = trendEngine;
    }

    @Transactional
    void persistSession(
            UUID importJobId,
            UUID sessionId,
            UUID vehicleId,
            ParsedSession parsed,
            String parquetPath) {
        importJobRepository.updateStage(importJobId, ImportJobRepository.STAGE_PERSISTING);

        sessionRepository.insert(
                sessionId,
                vehicleId,
                importJobId,
                parsed.source(),
                parsed.driveStartedAt(),
                parsed.driveEndedAt(),
                parsed.samples().size(),
                parquetPath);

        TripSegmenter.TripSegmentation segmentation =
                tripSegmenter.segment(parsed.samples(), parsed.driveStartedAt());
        List<SessionAggregateRow> aggregates =
                SessionAggregateCalculator.aggregatesForSegments(segmentation.segments());
        if (!aggregates.isEmpty()) {
            sessionAggregateRepository.insertAll(sessionId, aggregates);
        }

        List<SessionAggregateView> aggregateViews = aggregates.stream()
                .map(row -> new SessionAggregateView(
                        row.segment(), row.pidName(), row.mean(), row.std(), row.min(), row.max(), row.n()))
                .toList();
        trendEngine.processAfterImport(
                vehicleId, sessionId, aggregateViews, parsed.samples(), parsed.driveStartedAt());

        importJobRepository.markCompleted(importJobId, parquetPath);
    }
}
