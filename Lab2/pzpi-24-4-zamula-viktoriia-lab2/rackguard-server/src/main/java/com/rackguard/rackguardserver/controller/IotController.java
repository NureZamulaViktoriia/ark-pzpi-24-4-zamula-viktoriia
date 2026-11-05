package com.rackguard.rackguardserver.controller;

import com.rackguard.rackguardserver.security.Auth;
import com.rackguard.rackguardserver.service.EventService;
import com.rackguard.rackguardserver.service.RequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/iot")
@Tag(name = "IoT (ESP32)")
public class IotController {
    public record HeartbeatBody(String firmware) {}
    public record ResultBody(@NotBlank String status, String reason) {}
    public record EventsBody(@NotEmpty List<EventService.DeviceEvent> events) {}
    public record RfidBody(@NotBlank String uidHash) {}

    private final EventService events;
    private final RequestService requests;

    public IotController(EventService events, RequestService requests) {
        this.events = events;
        this.requests = requests;
    }

    @PostMapping("/heartbeat")
    public Map<String, Long> heartbeat(@RequestBody HeartbeatBody b) {
        events.heartbeat(Auth.device().deviceId(), b.firmware());
        return Map.of("serverEpoch", Instant.now().getEpochSecond());
    }

    @GetMapping("/commands")
    public List<RequestService.DeviceCommand> commands() { return requests.pendingCommands(Auth.device().doorId()); }

    @PostMapping("/commands/{nonce}/result")
    public void result(@PathVariable String nonce, @Valid @RequestBody ResultBody b) {
        requests.commandResult(Auth.device().doorId(), nonce, b.status(), b.reason());
    }

    @PostMapping("/events")
    public EventService.IngestReport events(@Valid @RequestBody EventsBody b) { return events.ingest(Auth.device(), b.events()); }

    @PostMapping("/rfid")
    public EventService.RfidResult rfid(@Valid @RequestBody RfidBody b) { return events.checkRfid(Auth.device(), b.uidHash()); }
}