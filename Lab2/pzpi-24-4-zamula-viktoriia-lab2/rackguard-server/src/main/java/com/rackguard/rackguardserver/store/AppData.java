package com.rackguard.rackguardserver.store;

import java.time.LocalDateTime;
import java.util.*;

public class AppData {
    public Settings settings = new Settings(8, 30, 30.0, 12);
    public List<Prefs> prefs = new ArrayList<>();
    public List<Consent> consents = new ArrayList<>();
    public List<GdprRequest> gdpr = new ArrayList<>();
    public List<Audit> audit = new ArrayList<>();
    public List<LlmLog> llmLog = new ArrayList<>();
    public List<Cert> certs = new ArrayList<>();
    public List<Backup> backups = new ArrayList<>();
    public List<Checklist> checklists = new ArrayList<>();
    public Set<String> archived = new LinkedHashSet<>();
    public Map<Integer, String> notes = new HashMap<>();

    public record Settings(int maxVisitHours, int commandTtlSeconds, double temperatureThresholdC, int retentionMonths) {}
    public record Prefs(Integer userId, String language, String unitSystem, String timeZone) {}
    public record Consent(Integer userId, String policyVersion, LocalDateTime grantedAt, LocalDateTime revokedAt) {}
    public record GdprRequest(Integer requestId, Integer userId, String type, String status,
                              LocalDateTime createdAt, String resultJson) {}
    public record Audit(LocalDateTime at, Integer actorId, String action, String target) {}
    public record LlmLog(LocalDateTime at, Integer actorId, String kind, String prompt, String response) {}
    public record Cert(Integer certId, String kind, String subject, String serial, LocalDateTime notAfter, String status) {}
    public record Backup(Integer backupId, String fileName, String path, LocalDateTime createdAt) {}
    public record Checklist(Integer requestId, boolean doorClosed, boolean equipmentUnchanged,
                            boolean noStrangers, String remarks, LocalDateTime at) {}
}
