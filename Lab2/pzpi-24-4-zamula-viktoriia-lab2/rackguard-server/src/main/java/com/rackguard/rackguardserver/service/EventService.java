package com.rackguard.rackguardserver.service;

import com.rackguard.rackguardserver.model.DoorEvent;
import com.rackguard.rackguardserver.model.User;
import com.rackguard.rackguardserver.repository.CatalogRepository;
import com.rackguard.rackguardserver.repository.EventRepository;
import com.rackguard.rackguardserver.repository.RequestRepository;
import com.rackguard.rackguardserver.repository.UserRepository;
import com.rackguard.rackguardserver.repository.DbObjectsRepository;
import com.rackguard.rackguardserver.security.Auth.DevicePrincipal;
import com.rackguard.rackguardserver.store.AppData.Prefs;
import com.rackguard.rackguardserver.store.DataStore;
import com.rackguard.rackguardserver.util.Const;
import com.rackguard.rackguardserver.util.Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventService {
    public record DeviceEvent(String eventKey, String type, Instant occurredAt, Double value, String note) {}
    public record IngestReport(int accepted, int duplicates, int rejected) {}
    public record RfidResult(boolean granted, String reason) {}
    public record View(Integer eventId, Integer doorId, Integer userId, Integer requestId, String eventType,
                       OffsetDateTime eventTime, String eventTimeDisplay, boolean anomaly, String note) {}
    public record Page(List<View> items, int page, int size, long total) {}
    public record DeviceStatus(Integer doorId, String doorName, String deviceId, boolean online,
                               OffsetDateTime lastSeen, String firmware) {}
    public record Health(String api, String database, List<DeviceStatus> devices) {}
    private record Beat(Instant at, String firmware) {}

    private static final int PAGE_SIZE = 50;
    private final Map<String, Beat> beats = new ConcurrentHashMap<>();
    private final Set<String> seen = new LinkedHashSet<>();

    private final EventRepository events;
    private final RequestRepository requests;
    private final UserRepository users;
    private final CatalogRepository catalog;
    private final DbObjectsRepository db;
    private final PrefsService prefs;
    private final DataStore store;
    private final Duration offlineAfter;

    public EventService(EventRepository events, RequestRepository requests, UserRepository users,
                        CatalogRepository catalog, DbObjectsRepository db, PrefsService prefs, DataStore store,
                        @Value("${app.device.offline-seconds}") int offlineSeconds) {
        this.events = events;
        this.requests = requests;
        this.users = users;
        this.catalog = catalog;
        this.db = db;
        this.prefs = prefs;
        this.store = store;
        this.offlineAfter = Duration.ofSeconds(offlineSeconds);
    }

    public IngestReport ingest(DevicePrincipal device, List<DeviceEvent> batch) {
        int accepted = 0, duplicates = 0, rejected = 0;
        List<DeviceEvent> sorted = batch.stream()
                .sorted(Comparator.comparing(e -> e.occurredAt() == null ? Instant.now() : e.occurredAt())).toList();
        for (DeviceEvent e : sorted) {
            if (!Const.DEVICE_EVENTS.contains(e.type())) { rejected++; continue; }
            if (!firstTime(device.deviceId() + ":" + e.eventKey())) { duplicates++; continue; }
            events.insert(build(device.doorId(), e));
            accepted++;
        }
        return new IngestReport(accepted, duplicates, rejected);
    }

    private boolean firstTime(String key) {
        synchronized (seen) {
            if (!seen.add(key)) return false;
            if (seen.size() > 5000) { Iterator<String> it = seen.iterator(); it.next(); it.remove(); }
            return true;
        }
    }

    private DoorEvent build(Integer doorId, DeviceEvent e) {
        LocalDateTime time = e.occurredAt() == null ? Util.nowUtc() : Util.fromInstant(e.occurredAt());
        Integer userId = null, requestId = null;
        boolean anomaly = false;
        String note = e.note();
        switch (e.type()) {
            case Const.OPENED -> {
                Optional<DoorEvent> auth = events.findLastAuthorization(doorId, time, Const.AUTHORIZATION_WINDOW_SECONDS);
                if (auth.isEmpty()) { anomaly = true; note = "ANOMALY:OPEN_WITHOUT_COMMAND"; }
                else {
                    userId = auth.get().userId();
                    requestId = auth.get().requestId();
                    boolean outside = requestId != null && requests.findById(requestId)
                            .map(r -> time.isBefore(r.startTime()) || time.isAfter(r.endTime())).orElse(false);
                    if (outside) { anomaly = true; note = "ANOMALY:OUTSIDE_WINDOW"; }
                }
            }
            case Const.CLOSED -> {
                Optional<DoorEvent> opened = events.findLastOpened(doorId, time);
                if (opened.isPresent()) {
                    userId = opened.get().userId();
                    requestId = opened.get().requestId();
                    long seconds = Duration.between(opened.get().eventTime(), time).getSeconds();
                    if (seconds > Const.OPEN_TOO_LONG_SECONDS) { anomaly = true; note = "ANOMALY:OPEN_TOO_LONG:" + seconds + "s"; }
                }
            }
            case Const.TAMPER -> { anomaly = true; note = "ANOMALY:TAMPER"; }
            case Const.TEMPERATURE -> {
                double c = e.value() == null ? Double.NaN : e.value();
                note = "temp=" + c;
                if (c > store.read(d -> d.settings.temperatureThresholdC())) { anomaly = true; note = "ANOMALY:TEMP_HIGH temp=" + c; }
            }
            default -> { }
        }
        if (note != null && note.length() > 255) note = note.substring(0, 255);
        return new DoorEvent(null, doorId, userId, requestId, e.type(), time, anomaly, note);
    }

    public RfidResult checkRfid(DevicePrincipal device, String uidHash) {
        Integer roomId = catalog.findDoor(device.doorId()).map(d -> d.roomId()).orElse(null);
        Optional<User> user = users.findByRfid(uidHash).filter(User::isActive);
        Optional<Integer> permission = user.isEmpty() || roomId == null ? Optional.empty()
                : requests.findActiveApproved(user.get().userId(), roomId)
                .or(() -> requests.findActiveConfirmed(user.get().userId(), roomId, Util.nowUtc()));
        boolean granted = permission.isPresent();
        events.insert(new DoorEvent(null, device.doorId(), user.map(User::userId).orElse(null), permission.orElse(null),
                granted ? Const.RFID_GRANTED : Const.RFID_DENIED, Util.nowUtc(), false, granted ? null : "no active permission"));
        return new RfidResult(granted, granted ? "OK" : "DENIED");
    }

    private View view(DoorEvent e, Prefs p) {
        return new View(e.eventId(), e.doorId(), e.userId(), e.requestId(), e.eventType(), prefs.toOffset(e.eventTime(), p),
                prefs.display(e.eventTime(), p), e.isAnomaly(), e.note());
    }

    public Page page(EventRepository.Filter f, int page, Integer adminId) {
        if (f.userId() != null) store.audit(adminId, "PERSONAL_DATA_VIEW", "user:" + f.userId());
        Prefs p = prefs.get(adminId);
        List<View> items = events.findPage(f, Math.max(page, 0), PAGE_SIZE).stream().map(e -> view(e, p)).toList();
        return new Page(items, page, PAGE_SIZE, events.count(f));
    }

    public Util.FileData export(EventRepository.Filter f, String format, Integer adminId) {
        List<DoorEvent> list = events.find(f);
        store.audit(adminId, "EVENTS_EXPORT", format + ", rows=" + list.size());
        List<List<Object>> rows = list.stream().<List<Object>>map(e -> Arrays.asList(e.eventId(), e.doorId(), e.userId(),
                e.requestId(), e.eventType(), e.eventTime(), e.isAnomaly(), e.note())).toList();
        return Util.export(format, "events", List.of("eventId", "doorId", "userId", "requestId", "eventType",
                "eventTimeUtc", "isAnomaly", "note"), rows);
    }


    public void heartbeat(String deviceId, String firmware) { beats.put(deviceId, new Beat(Instant.now(), firmware)); }

    public List<DeviceStatus> devices(Prefs p) {
        return catalog.findDoors().stream().filter(d -> d.esp32DeviceId() != null).map(d -> {
            Beat b = beats.get(d.esp32DeviceId());
            boolean online = b != null && Duration.between(b.at(), Instant.now()).compareTo(offlineAfter) <= 0;
            return new DeviceStatus(d.doorId(), d.name(), d.esp32DeviceId(), online,
                    b == null ? null : prefs.toOffset(Util.fromInstant(b.at()), p), b == null ? null : b.firmware());
        }).toList();
    }

    public Health health(Prefs p) { return new Health("UP", db.ping() ? "UP" : "DOWN", devices(p)); }
}
