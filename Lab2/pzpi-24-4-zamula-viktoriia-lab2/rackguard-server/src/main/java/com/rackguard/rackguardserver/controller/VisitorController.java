package com.rackguard.rackguardserver.controller;

import com.rackguard.rackguardserver.model.Room;
import com.rackguard.rackguardserver.security.Auth;
import com.rackguard.rackguardserver.service.CatalogService;
import com.rackguard.rackguardserver.service.RequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@Tag(name = "Visitor (Mobile)")
public class VisitorController {
    public record CreateBody(@NotNull Integer roomId, @NotBlank String purpose,
                             @NotNull OffsetDateTime startTime, @NotNull OffsetDateTime endTime) {}
    public record ConfirmBody(@NotNull Integer requestId, @NotBlank String qrPayload) {}
    public record OpenBody(@NotNull Integer requestId) {}
    public record ChecklistBody(boolean doorClosed, boolean equipmentUnchanged, boolean noStrangers, String remarks) {}

    private final RequestService requests;
    private final CatalogService catalog;

    public VisitorController(RequestService requests, CatalogService catalog) {
        this.requests = requests;
        this.catalog = catalog;
    }

    @GetMapping("/api/rooms")
    public List<Room> rooms() { return catalog.rooms(false); }

    @PostMapping("/api/requests")
    public RequestService.View create(@Valid @RequestBody CreateBody b) {
        return requests.create(Auth.userId(), b.roomId(), b.purpose(), b.startTime(), b.endTime());
    }

    @GetMapping("/api/requests/my")
    public List<RequestService.View> mine() { return requests.mine(Auth.userId()); }

    @PostMapping("/api/requests/{id}/cancel")
    public RequestService.View cancel(@PathVariable Integer id) { return requests.cancelByVisitor(Auth.userId(), id); }

    @PostMapping("/api/visits/confirm")
    public RequestService.ConfirmResult confirm(@Valid @RequestBody ConfirmBody b) {
        return requests.confirm(Auth.userId(), b.requestId(), b.qrPayload());
    }

    @PostMapping("/api/visits/open")
    public RequestService.OpenResult open(@Valid @RequestBody OpenBody b) { return requests.open(Auth.userId(), b.requestId()); }

    @GetMapping("/api/visits/commands/{commandId}")
    public RequestService.CommandState state(@PathVariable String commandId) {
        return requests.commandState(Auth.userId(), commandId);
    }

    @PostMapping("/api/visits/{requestId}/checklist")
    public RequestService.View checklist(@PathVariable Integer requestId, @RequestBody ChecklistBody b) {
        return requests.checklist(Auth.userId(), requestId, b.doorClosed(), b.equipmentUnchanged(), b.noStrangers(), b.remarks());
    }
}