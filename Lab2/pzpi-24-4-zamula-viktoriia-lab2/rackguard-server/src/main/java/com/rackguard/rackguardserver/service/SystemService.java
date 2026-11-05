package com.rackguard.rackguardserver.service;

import com.rackguard.rackguardserver.exception.ApiException;
import com.rackguard.rackguardserver.repository.EventRepository;
import com.rackguard.rackguardserver.store.AppData;
import com.rackguard.rackguardserver.store.DataStore;
import com.rackguard.rackguardserver.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
public class SystemService {
    public record CertView(AppData.Cert cert, long daysLeft, boolean expiringSoon) {}

    private static final Logger log = LoggerFactory.getLogger(SystemService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final DataStore store;
    private final JdbcTemplate jdbc;
    private final EventRepository events;
    private final String backupDir, masterUrl, database, dbUser, dbPassword;
    private final int keep;

    public SystemService(DataStore store, JdbcTemplate jdbc, EventRepository events,
                         @Value("${app.backup.directory}") String backupDir, @Value("${app.backup.master-url}") String masterUrl,
                         @Value("${app.backup.database}") String database, @Value("${app.backup.keep}") int keep,
                         @Value("${spring.datasource.username}") String dbUser, @Value("${spring.datasource.password}") String dbPassword) {
        if (!database.matches("[A-Za-z0-9_]+")) throw new IllegalStateException("Bad database name");
        this.store = store;
        this.jdbc = jdbc;
        this.events = events;
        this.backupDir = backupDir;
        this.masterUrl = masterUrl;
        this.database = database;
        this.keep = keep;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public AppData.Settings settings() { return store.read(d -> d.settings); }

    public AppData.Settings updateSettings(AppData.Settings s, Integer actorId) {
        if (s.maxVisitHours() < 1 || s.maxVisitHours() > 24 || s.commandTtlSeconds() < 5 || s.commandTtlSeconds() > 120
                || s.temperatureThresholdC() < 10 || s.temperatureThresholdC() > 80
                || s.retentionMonths() < 1 || s.retentionMonths() > 120) {
            throw ApiException.bad("settings.invalid");
        }
        store.write(d -> d.settings = s);
        store.audit(actorId, "SETTINGS_UPDATE", s.toString());
        return s;
    }

    public List<AppData.Audit> audit(LocalDateTime from, LocalDateTime to) {
        return store.read(d -> d.audit.stream().filter(a -> !a.at().isBefore(from) && a.at().isBefore(to)).toList());
    }

    private CertView certView(AppData.Cert c) {
        long days = Duration.between(Util.nowUtc(), c.notAfter()).toDays();
        return new CertView(c, days, "ACTIVE".equals(c.status()) && days <= 30);
    }

    public List<CertView> certificates() { return store.read(d -> d.certs.stream().map(this::certView).toList()); }

    public List<CertView> certificateWarnings() { return certificates().stream().filter(CertView::expiringSoon).toList(); }

    public AppData.Cert issueCertificate(String kind, String subject, int validDays, Integer actorId) {
        AppData.Cert c = store.update(d -> {
            int id = d.certs.stream().mapToInt(AppData.Cert::certId).max().orElse(0) + 1;
            AppData.Cert cert = new AppData.Cert(id, kind.toUpperCase(), subject, Util.randomHex(8),
                    Util.nowUtc().plusDays(validDays), "ACTIVE");
            d.certs.add(cert);
            return cert;
        });
        store.audit(actorId, "CERT_ISSUE", "cert:" + c.certId());
        return c;
    }

    public void revokeCertificate(Integer id, Integer actorId) {
        boolean found = store.update(d -> {
            boolean[] changed = {false};
            d.certs.replaceAll(c -> {
                if (!c.certId().equals(id)) return c;
                changed[0] = true;
                return new AppData.Cert(c.certId(), c.kind(), c.subject(), c.serial(), c.notAfter(), "REVOKED");
            });
            return changed[0];
        });
        if (!found) throw ApiException.notFound("error.notFound");
        store.audit(actorId, "CERT_REVOKE", "cert:" + id);
    }

    public List<AppData.Backup> backups() {
        return store.read(d -> d.backups.stream().sorted(Comparator.comparing(AppData.Backup::createdAt).reversed()).toList());
    }

    public synchronized AppData.Backup createBackup(String trigger, Integer actorId) {
        String file = database + "_" + Util.nowUtc().format(STAMP) + ".bak";
        String path = Path.of(backupDir, file).toString();
        jdbc.execute("BACKUP DATABASE [" + database + "] TO DISK = N'" + path.replace("'", "''") + "' WITH INIT, CHECKSUM");
        AppData.Backup b = store.update(d -> {
            int id = d.backups.stream().mapToInt(AppData.Backup::backupId).max().orElse(0) + 1;
            AppData.Backup rec = new AppData.Backup(id, file, path, Util.nowUtc());
            d.backups.add(rec);
            return rec;
        });
        store.audit(actorId, "BACKUP_CREATE", "backup:" + b.backupId() + " " + trigger);
        for (AppData.Backup old : backups().stream().skip(keep).toList()) {      // keep the last N copies
            try { Files.deleteIfExists(Path.of(old.path())); } catch (IOException ignored) { /* best effort */ }
            store.write(d -> d.backups.removeIf(x -> x.backupId().equals(old.backupId())));
        }
        return b;
    }

    public void restore(Integer id, String confirm, Integer actorId) {
        if (!"RESTORE".equals(confirm)) throw ApiException.bad("backup.confirm");
        AppData.Backup b = store.read(d -> d.backups.stream().filter(x -> x.backupId().equals(id)).findFirst())
                .orElseThrow(() -> ApiException.notFound("error.notFound"));
        store.audit(actorId, "BACKUP_RESTORE", "backup:" + id);
        try (Connection c = DriverManager.getConnection(masterUrl, dbUser, dbPassword); Statement st = c.createStatement()) {
            st.execute("ALTER DATABASE [" + database + "] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
            try {
                st.execute("RESTORE DATABASE [" + database + "] FROM DISK = N'" + b.path().replace("'", "''") + "' WITH REPLACE");
            } finally {
                st.execute("ALTER DATABASE [" + database + "] SET MULTI_USER");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Restore failed", e);
        }
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void nightlyBackup() {
        try { createBackup("SCHEDULED", null); }
        catch (RuntimeException e) { log.error("Nightly backup failed: {}", e.getClass().getSimpleName()); }
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void retention() {
        int n = events.anonymizeOlderThan(Util.nowUtc().minusMonths(settings().retentionMonths()));
        if (n > 0) log.info("Retention: {} events anonymized", n);
    }
}