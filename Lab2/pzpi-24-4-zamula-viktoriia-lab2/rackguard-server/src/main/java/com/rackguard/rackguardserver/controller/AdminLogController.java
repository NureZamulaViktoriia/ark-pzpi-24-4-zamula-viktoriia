package com.rackguard.rackguardserver.controller;

import com.rackguard.rackguardserver.repository.DbObjectsRepository;
import com.rackguard.rackguardserver.repository.EventRepository;
import com.rackguard.rackguardserver.security.Auth;
import com.rackguard.rackguardserver.service.AnalyticsService;
import com.rackguard.rackguardserver.service.EventService;
import com.rackguard.rackguardserver.service.LlmService;
import com.rackguard.rackguardserver.store.AppData;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin: log, analytics, LLM")
public class AdminLogController {
    public record PeriodBody(@NotNull LocalDateTime from, @NotNull LocalDateTime to) {}
    private static final String ISO = "yyyy-MM-dd'T'HH:mm:ss";

    private final EventService events;
    private final DbObjectsRepository db;
    private final AnalyticsService analytics;
    private final LlmService llm;

    public AdminLogController(EventService events, DbObjectsRepository db, AnalyticsService analytics, LlmService llm) {
        this.events = events;
        this.db = db;
        this.analytics = analytics;
        this.llm = llm;
    }

    @GetMapping("/events")
    public EventService.Page page(@RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDateTime from,
                                  @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDateTime to,
                                  @RequestParam(required = false) Integer doorId, @RequestParam(required = false) Integer userId,
                                  @RequestParam(required = false) String type, @RequestParam(defaultValue = "0") int page) {
        return events.page(new EventRepository.Filter(from, to, doorId, userId, type), page, Auth.userId());
    }

    @GetMapping("/export/events")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDateTime from,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = ISO) LocalDateTime to,
                                         @RequestParam(required = false) Integer doorId, @RequestParam(required = false) Integer userId,
                                         @RequestParam(required = false) String type, @RequestParam(defaultValue = "csv") String format) {
        return AdminCatalogController.download(
                events.export(new EventRepository.Filter(from, to, doorId, userId, type), format, Auth.userId()));
    }

    @GetMapping("/anomalies")
    public List<DbObjectsRepository.AnomalyRow> anomalies(@RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime from,
                                                          @RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime to) {
        return db.anomalies(from, to);
    }

    @GetMapping("/analytics/visits")
    public List<AnalyticsService.Aggregate> visits(@RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime from,
                                                   @RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime to,
                                                   @RequestParam(defaultValue = "DAY") AnalyticsService.Period period) {
        return analytics.aggregate(from, to, period);
    }

    @GetMapping("/analytics/stats")
    public AnalyticsService.Stats stats(@RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime from,
                                        @RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime to) {
        return analytics.stats(from, to);
    }

    @GetMapping("/analytics/dashboard")
    public AnalyticsService.Dashboard dashboard(@RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime from,
                                                @RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime to) {
        return analytics.dashboard(from, to, Auth.userId());
    }

    @PostMapping("/llm/summary")
    public LlmService.Result summary(@Valid @RequestBody PeriodBody b) { return llm.summary(b.from(), b.to(), Auth.userId()); }

    @PostMapping("/llm/explain")
    public LlmService.Result explain(@Valid @RequestBody PeriodBody b) { return llm.explain(b.from(), b.to(), Auth.userId()); }

    @GetMapping("/llm/log")
    public List<AppData.LlmLog> llmLog() { return llm.history(); }
}
