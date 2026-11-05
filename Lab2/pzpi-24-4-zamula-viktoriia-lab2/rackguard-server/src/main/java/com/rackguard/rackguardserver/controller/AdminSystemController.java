package com.rackguard.rackguardserver.controller;

import com.rackguard.rackguardserver.repository.DbObjectsRepository;
import com.rackguard.rackguardserver.security.Auth;
import com.rackguard.rackguardserver.service.*;
import com.rackguard.rackguardserver.store.AppData;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin: system, GDPR, DB objects")
public class AdminSystemController {
    public record CertBody(@NotBlank String kind, @NotBlank String subject, @Min(1) @Max(3650) int validDays) {}
    public record RestoreBody(String confirm) {}
    public record SimpleEventBody(@NotNull Integer doorId, @NotBlank String eventType) {}

    private static final String ISO = "yyyy-MM-dd'T'HH:mm:ss";
    private final SystemService system;
    private final GdprService gdpr;
    private final EventService events;
    private final PrefsService prefs;
    private final DbObjectsRepository db;

    public AdminSystemController(SystemService system, GdprService gdpr, EventService events, PrefsService prefs,
                                 DbObjectsRepository db) {
        this.system = system;
        this.gdpr = gdpr;
        this.events = events;
        this.prefs = prefs;
        this.db = db;
    }

    @GetMapping("/settings") public AppData.Settings settings() { return system.settings(); }
    @PutMapping("/settings") public AppData.Settings settings(@RequestBody AppData.Settings s) { return system.updateSettings(s, Auth.userId()); }

    @GetMapping("/backups") public List<AppData.Backup> backups() { return system.backups(); }
    @PostMapping("/backups") public AppData.Backup backup() { return system.createBackup("MANUAL", Auth.userId()); }
    @PostMapping("/backups/{id}/restore")
    public void restore(@PathVariable Integer id, @RequestBody RestoreBody b) { system.restore(id, b.confirm(), Auth.userId()); }

    @GetMapping("/certificates") public List<SystemService.CertView> certs() { return system.certificates(); }
    @PostMapping("/certificates")
    public AppData.Cert issue(@Valid @RequestBody CertBody b) { return system.issueCertificate(b.kind(), b.subject(), b.validDays(), Auth.userId()); }
    @PostMapping("/certificates/{id}/revoke") public void revoke(@PathVariable Integer id) { system.revokeCertificate(id, Auth.userId()); }

    @GetMapping("/health")
    public EventService.Health health() { return events.health(prefs.get(Auth.userId())); }

    @GetMapping("/audit")
    public List<AppData.Audit> audit(@RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime from,
                                     @RequestParam @DateTimeFormat(pattern = ISO) LocalDateTime to) {
        return system.audit(from, to);
    }

    @GetMapping("/gdpr/requests") public List<AppData.GdprRequest> gdprRequests() { return gdpr.listAll(); }
    @PostMapping("/gdpr/requests/{id}/execute")
    public AppData.GdprRequest execute(@PathVariable Integer id) { return gdpr.execute(Auth.userId(), id); }

    @GetMapping("/db/functions/approved-by-room") public int approved(@RequestParam String room) { return db.totalApprovedByRoom(room); }
    @GetMapping("/db/functions/active-users") public int activeUsers(@RequestParam String role) { return db.activeUsersByRole(role); }
    @GetMapping("/db/functions/events-after-average") public List<DbObjectsRepository.EventBrief> afterAverage() { return db.eventsAfterAverageTime(); }
    @PostMapping("/db/procedures/categorize-events/{doorId}") public List<String> categorize(@PathVariable Integer doorId) { return db.categorizeEvents(doorId); }
    @PostMapping("/db/procedures/safe-insert-event")
    public List<String> safeInsert(@Valid @RequestBody SimpleEventBody b) { return db.safeInsertEvent(b.doorId(), b.eventType()); }
    @GetMapping("/db/cursors/every-second-event/{doorId}") public List<String> everySecond(@PathVariable Integer doorId) { return db.fetchEverySecondEvent(doorId); }
    @DeleteMapping("/db/cursors/rejected-requests")
    public List<String> deleteRejected(@RequestParam Integer roomId, @RequestParam int k) { return db.deleteRejectedCursor(roomId, k); }
}