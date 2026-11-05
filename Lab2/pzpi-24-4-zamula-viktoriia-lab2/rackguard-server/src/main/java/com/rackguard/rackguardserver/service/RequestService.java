package com.rackguard.rackguardserver.service;

import com.rackguard.rackguardserver.exception.ApiException;
import com.rackguard.rackguardserver.model.AccessRequest;
import com.rackguard.rackguardserver.model.Door;
import com.rackguard.rackguardserver.model.DoorEvent;
import com.rackguard.rackguardserver.model.User;
import com.rackguard.rackguardserver.repository.EventRepository;
import com.rackguard.rackguardserver.repository.RequestRepository;
import com.rackguard.rackguardserver.repository.UserRepository;
import com.rackguard.rackguardserver.store.AppData;
import com.rackguard.rackguardserver.store.AppData.Prefs;
import com.rackguard.rackguardserver.store.DataStore;
import com.rackguard.rackguardserver.util.Const;
import com.rackguard.rackguardserver.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
public class RequestService {
    public record View(Integer requestId, Integer userId, Integer roomId, Integer approverId, String purpose,
                       OffsetDateTime startTime, String startDisplay, OffsetDateTime endTime, String endDisplay,
                       String status, String note) {}
    public record ConfirmResult(Integer doorId, String bleToken, OffsetDateTime windowEnd) {}
    public record OpenResult(String commandId, long expiresAt) {}
    public record CommandState(String commandId, String status, String reason) {}

    public record DeviceCommand(String nonce, String deviceId, Integer doorId, long expiresAt,
                                String tokenHash, String signature) {}
    private record Session(Integer doorId, String token) {}
    private record Command(DeviceCommand cmd, Integer requestId, Integer userId, String state, String reason) {}

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);
    private final Map<Integer, Session> sessions = new ConcurrentHashMap<>();     // requestId -> QR door + BLE token
    private final Map<String, Command> commands = new ConcurrentHashMap<>();       // nonce -> command

    private final RequestRepository requests;
    private final UserRepository users;
    private final EventRepository events;
    private final CatalogService catalog;
    private final PrefsService prefs;
    private final DataStore store;

    public RequestService(RequestRepository requests, UserRepository users, EventRepository events,
                          CatalogService catalog, PrefsService prefs, DataStore store) {
        this.requests = requests;
        this.users = users;
        this.events = events;
        this.catalog = catalog;
        this.prefs = prefs;
        this.store = store;
    }

    private AccessRequest require(Integer id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("request.notFound"));
    }

    private AccessRequest own(Integer userId, Integer id) {
        AccessRequest r = require(id);
        if (!r.userId().equals(userId)) throw ApiException.forbidden("request.notOwner");
        return r;
    }

    private View view(AccessRequest r, Prefs p) {
        return new View(r.requestId(), r.userId(), r.roomId(), r.approverId(), r.purpose(),
                prefs.toOffset(r.startTime(), p), prefs.display(r.startTime(), p),
                prefs.toOffset(r.endTime(), p), prefs.display(r.endTime(), p), r.status(),
                store.read(d -> d.notes.get(r.requestId())));
    }

    private void requireInWindow(AccessRequest r) {
        LocalDateTime now = Util.nowUtc();
        if (now.isBefore(r.startTime()) || now.isAfter(r.endTime())) throw ApiException.conflict("visit.outsideWindow");
    }

    private AppData.Settings settings() { return store.read(d -> d.settings); }

    private void changeStatus(AccessRequest r, String status) {
        if (!catalog.hasDoor(r.roomId())) throw ApiException.conflict("request.roomNoDoor");
        requests.updateStatus(r.requestId(), status);
        if (!List.of(Const.APPROVED, Const.CONFIRMED).contains(status)) revokeCommands(r.requestId());
    }

    private void revokeCommands(Integer requestId) {
        sessions.remove(requestId);
        commands.replaceAll((k, c) -> c.requestId().equals(requestId) && "PENDING".equals(c.state())
                ? new Command(c.cmd(), c.requestId(), c.userId(), "REVOKED", "permission ended") : c);
    }

    public View create(Integer userId, Integer roomId, String purpose, OffsetDateTime startAt, OffsetDateTime endAt) {
        catalog.requireActiveRoom(roomId);
        LocalDateTime start = Util.toUtc(startAt), end = Util.toUtc(endAt);
        int maxHours = settings().maxVisitHours();
        if (!end.isAfter(start)) throw ApiException.bad("request.invalidWindow");
        if (start.isBefore(Util.nowUtc())) throw ApiException.bad("request.past");
        if (Duration.between(start, end).toMinutes() > maxHours * 60L) throw ApiException.bad("request.tooLong", maxHours);
        if (!requests.findOverlapping(userId, start, end).isEmpty()) throw ApiException.conflict("request.overlap");
        Integer id = requests.insertViaProc(userId, roomId, purpose, start, end);
        if (id == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal");
        return view(require(id), prefs.get(userId));
    }

    public List<View> mine(Integer userId) {
        Prefs p = prefs.get(userId);
        return requests.findByUser(userId).stream().map(r -> view(r, p)).toList();
    }

    public View cancelByVisitor(Integer userId, Integer id) {
        AccessRequest r = own(userId, id);
        if (!List.of(Const.CREATED, Const.APPROVED).contains(r.status())) throw ApiException.conflict("request.badStatus", r.status());
        if (!r.startTime().isAfter(Util.nowUtc())) throw ApiException.conflict("request.cancelAfterStart");
        changeStatus(r, Const.CANCELED);
        return view(require(id), prefs.get(userId));
    }

    public List<View> queue(String status, Integer adminId) {
        Prefs p = prefs.get(adminId);
        return requests.findByStatus(status).stream().map(r -> view(r, p)).toList();
    }

    private void putNote(Integer id, String note) {
        if (note != null && !note.isBlank()) store.write(d -> d.notes.put(id, note));
    }

    public View approve(Integer adminId, Integer id, String comment) {
        AccessRequest r = require(id);
        if (!Const.CREATED.equals(r.status())) throw ApiException.conflict("request.badStatus", r.status());
        if (r.userId().equals(adminId)) throw ApiException.forbidden("request.selfApprove");
        if (!catalog.hasDoor(r.roomId())) throw ApiException.conflict("request.roomNoDoor");
        requests.decide(id, adminId, Const.APPROVED);
        putNote(id, comment);
        store.audit(adminId, "REQUEST_APPROVE", "request:" + id);
        return view(require(id), prefs.get(adminId));
    }

    public View reject(Integer adminId, Integer id, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.bad("request.reasonRequired");
        AccessRequest r = require(id);
        if (!Const.CREATED.equals(r.status())) throw ApiException.conflict("request.badStatus", r.status());
        if (r.userId().equals(adminId)) throw ApiException.forbidden("request.selfApprove");
        if (!catalog.hasDoor(r.roomId())) throw ApiException.conflict("request.roomNoDoor");
        requests.decide(id, adminId, Const.REJECTED);
        putNote(id, reason);
        store.audit(adminId, "REQUEST_REJECT", "request:" + id);
        return view(require(id), prefs.get(adminId));
    }

    public View cancelPermission(Integer adminId, Integer id, String reason) {
        if (reason == null || reason.isBlank()) throw ApiException.bad("request.reasonRequired");
        AccessRequest r = require(id);
        if (!List.of(Const.APPROVED, Const.CONFIRMED).contains(r.status())) throw ApiException.conflict("request.badStatus", r.status());
        changeStatus(r, Const.CANCELED);
        putNote(id, reason);
        store.audit(adminId, "PERMISSION_CANCEL", "request:" + id);
        return view(require(id), prefs.get(adminId));
    }

    public List<View> calendar(Integer roomId, LocalDateTime weekStartUtc, Integer adminId) {
        catalog.requireActiveRoom(roomId);
        Prefs p = prefs.get(adminId);
        return requests.findByRoomBetween(roomId, weekStartUtc, weekStartUtc.plusDays(7)).stream().map(r -> view(r, p)).toList();
    }

    public ConfirmResult confirm(Integer userId, Integer requestId, String qrPayload) {
        AccessRequest r = own(userId, requestId);
        if (!List.of(Const.APPROVED, Const.CONFIRMED).contains(r.status())) throw ApiException.conflict("request.badStatus", r.status());
        requireInWindow(r);
        Door door = catalog.requireDoor(catalog.verifyQr(qrPayload));
        if (!door.roomId().equals(r.roomId())) throw ApiException.conflict("visit.wrongRoom");
        if (Const.APPROVED.equals(r.status())) changeStatus(r, Const.CONFIRMED);
        String token = Util.randomHex(16);
        sessions.put(requestId, new Session(door.doorId(), token));
        return new ConfirmResult(door.doorId(), token, prefs.toOffset(r.endTime(), prefs.get(userId)));
    }

    public OpenResult open(Integer userId, Integer requestId) {
        AccessRequest r = own(userId, requestId);
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("user.notFound"));
        if (!u.isActive()) throw ApiException.forbidden("auth.blocked");
        Session s = sessions.get(requestId);
        if (!Const.CONFIRMED.equals(r.status()) || s == null) throw ApiException.conflict("visit.notConfirmed");
        requireInWindow(r);
        Door door = catalog.requireDoor(s.doorId());
        if (door.esp32DeviceId() == null) throw ApiException.conflict("visit.notAllowed");

        String nonce = Util.randomHex(8);
        long expires = Instant.now().getEpochSecond() + settings().commandTtlSeconds();
        String tokenHash = Util.sha256Hex(s.token());
        String signed = nonce + "|" + door.esp32DeviceId() + "|" + door.doorId() + "|" + expires + "|" + tokenHash;
        DeviceCommand cmd = new DeviceCommand(nonce, door.esp32DeviceId(), door.doorId(), expires, tokenHash,
                Util.hmacHex(catalog.deviceSecret(door.esp32DeviceId()), signed));
        commands.put(nonce, new Command(cmd, requestId, userId, "PENDING", null));
        events.insert(new DoorEvent(null, door.doorId(), userId, requestId, Const.COMMAND_ISSUED,
                Util.nowUtc(), false, "nonce=" + nonce));
        return new OpenResult(nonce, expires);
    }

    public CommandState commandState(Integer userId, String nonce) {
        Command c = commands.get(nonce);
        if (c == null || !c.userId().equals(userId)) throw ApiException.notFound("command.notFound");
        boolean expired = "PENDING".equals(c.state()) && c.cmd().expiresAt() < Instant.now().getEpochSecond();
        return new CommandState(nonce, expired ? "EXPIRED" : c.state(), c.reason());
    }

    public View checklist(Integer userId, Integer requestId, boolean doorClosed, boolean equipmentUnchanged,
                          boolean noStrangers, String remarks) {
        AccessRequest r = own(userId, requestId);
        if (!Const.CONFIRMED.equals(r.status())) throw ApiException.conflict("request.badStatus", r.status());
        store.write(d -> d.checklists.add(new AppData.Checklist(requestId, doorClosed, equipmentUnchanged,
                noStrangers, remarks, Util.nowUtc())));
        changeStatus(r, Const.COMPLETED);
        return view(require(requestId), prefs.get(userId));
    }

    public List<DeviceCommand> pendingCommands(Integer doorId) {
        long now = Instant.now().getEpochSecond();
        return commands.values().stream().filter(c -> "PENDING".equals(c.state())
                && c.cmd().doorId().equals(doorId) && c.cmd().expiresAt() > now).map(Command::cmd).toList();
    }

    public void commandResult(Integer doorId, String nonce, String status, String reason) {
        commands.computeIfPresent(nonce, (k, c) -> "PENDING".equals(c.state()) && c.cmd().doorId().equals(doorId)
                ? new Command(c.cmd(), c.requestId(), c.userId(),
                "EXECUTED".equalsIgnoreCase(status) ? "EXECUTED" : "REJECTED", reason) : c);
    }

    @Scheduled(fixedDelay = 60_000)
    public void closeRequests() {
        LocalDateTime now = Util.nowUtc();
        requests.findDueForExpiry(now).forEach(r -> safely(r, Const.EXPIRED));
        requests.findDueForCompletion(now).forEach(r -> safely(r, Const.COMPLETED));
        long limit = Instant.now().getEpochSecond() - 300;
        commands.values().removeIf(c -> c.cmd().expiresAt() < limit);
    }

    private void safely(AccessRequest r, String status) {
        try {
            changeStatus(r, status);
        } catch (RuntimeException e) {
            log.warn("Cannot set {} for request {}: {}", status, r.requestId(), e.getClass().getSimpleName());
        }
    }
}
