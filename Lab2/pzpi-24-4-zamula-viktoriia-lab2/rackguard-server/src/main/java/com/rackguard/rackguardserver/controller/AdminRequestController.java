package com.rackguard.rackguardserver.controller;

import com.rackguard.rackguardserver.repository.DbObjectsRepository;
import com.rackguard.rackguardserver.security.Auth;
import com.rackguard.rackguardserver.service.RequestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin: requests and permissions")
public class AdminRequestController {
    public record CommentBody(String comment) {}
    public record ReasonBody(@NotBlank String reason) {}

    private final RequestService requests;
    private final DbObjectsRepository db;

    public AdminRequestController(RequestService requests, DbObjectsRepository db) {
        this.requests = requests;
        this.db = db;
    }

    @GetMapping("/requests")
    public List<RequestService.View> queue(@RequestParam(required = false) String status) { return requests.queue(status, Auth.userId()); }

    @PostMapping("/requests/{id}/approve")
    public RequestService.View approve(@PathVariable Integer id, @RequestBody(required = false) CommentBody b) {
        return requests.approve(Auth.userId(), id, b == null ? null : b.comment());
    }

    @PostMapping("/requests/{id}/reject")
    public RequestService.View reject(@PathVariable Integer id, @Valid @RequestBody ReasonBody b) {
        return requests.reject(Auth.userId(), id, b.reason());
    }

    @PostMapping("/permissions/{id}/cancel")
    public RequestService.View cancel(@PathVariable Integer id, @Valid @RequestBody ReasonBody b) {
        return requests.cancelPermission(Auth.userId(), id, b.reason());
    }

    @GetMapping("/rooms/{roomId}/calendar")
    public List<RequestService.View> calendar(@PathVariable Integer roomId,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime weekStart) {
        return requests.calendar(roomId, weekStart, Auth.userId());
    }

    @GetMapping("/permissions/active")
    public List<DbObjectsRepository.ActivePermission> active() { return db.activePermissions(); }
}
