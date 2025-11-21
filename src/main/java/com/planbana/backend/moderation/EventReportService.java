package com.planbana.backend.moderation;

import com.planbana.backend.events.Event;
import com.planbana.backend.events.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EventReportService {

    private final EventReportRepository repo;
    private final EventRepository eventRepo;
    private final AutoModerationService autoModeration;

    public EventReportService(
            EventReportRepository repo,
            EventRepository eventRepo,
            AutoModerationService autoModeration) {
        this.repo = repo;
        this.eventRepo = eventRepo;
        this.autoModeration = autoModeration;
    }

    // ================================
    // SAVE REPORT
    // ================================
    public EventReport saveReport(String eventId, String reporterId, String reason, String category) {
        EventReport report = new EventReport();
        report.setEventId(eventId);
        report.setReporterUserId(reporterId);
        report.setReason(reason);
        report.setCategory(category);
        return repo.save(report);
    }

    // ================================
    // CHECK DUPLICATE
    // ================================
    public boolean hasUserAlreadyReported(String eventId, String reporterId) {
        return repo.existsByEventIdAndReporterUserId(eventId, reporterId);
    }

    // ================================
    // AUTOMODERATION HOOK
    // ================================
    public void evaluateAutoModeration(String eventId) {

        long count = repo.countByEventId(eventId);
        Event event = eventRepo.findById(eventId).orElse(null);

        if (event == null)
            return;

        autoModeration.autoBlockEventIfThresholdReached(event, (int) count);

        if (autoModeration.isSpam(event)) {
            autoModeration.autoHideSuspiciousEvent(event, "AI_SPAM_DETECTED");
        }
    }

    // ================================

    // REPORT GETTERS

    // ================================
    public EventReport getReport(String id) {
        return repo.findById(id).orElseThrow();
    }

    public EventReport save(EventReport report) {
        return repo.save(report);
    }

    // ================================
    // MODERATION QUEUE SEARCH
    // ================================
    public Page<EventReport> searchReports(String status, String category, Pageable pageable) {

        boolean hasStatus = (status != null && !status.isBlank());
        boolean hasCategory = (category != null && !category.isBlank());

        if (hasStatus && hasCategory) {
            return repo.findByStatusAndCategory(
                    EventReport.Status.valueOf(status.toUpperCase()),
                    category,
                    pageable);
        }

        if (hasStatus) {
            return repo.findByStatus(
                    EventReport.Status.valueOf(status.toUpperCase()),
                    pageable);
        }

        if (hasCategory) {
            return repo.findByCategory(category, pageable);
        }

        return repo.findAll(pageable);
    }

    // ================================
    // LIST REPORTS FOR A SPECIFIC E
    // ============================

    public List<EventReport> listReportsForEvent(String eventId) {
        return repo.findByEventIdOrderByCreatedAtDesc(eventId);
    }

}
