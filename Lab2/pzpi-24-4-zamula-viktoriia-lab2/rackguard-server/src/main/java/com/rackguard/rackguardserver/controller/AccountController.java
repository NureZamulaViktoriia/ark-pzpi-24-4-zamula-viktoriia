package com.rackguard.rackguardserver.controller;

import com.rackguard.rackguardserver.security.Auth;
import com.rackguard.rackguardserver.service.*;
import com.rackguard.rackguardserver.store.AppData;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Account (all roles)")
public class AccountController {
    public record LoginBody(@NotBlank String email, @NotBlank String password) {}
    public record PrefsBody(@NotBlank String language, @NotBlank String unitSystem, @NotBlank String timeZone) {}
    public record ConsentBody(@NotBlank String policyVersion) {}
    public record GdprBody(@NotBlank String type) {}

    private final AccountService accounts;
    private final PrefsService prefs;
    private final GdprService gdpr;
    private final EventService events;

    public AccountController(AccountService accounts, PrefsService prefs, GdprService gdpr, EventService events) {
        this.accounts = accounts;
        this.prefs = prefs;
        this.gdpr = gdpr;
        this.events = events;
    }

    @PostMapping("/api/auth/login")
    public AccountService.LoginResult login(@Valid @RequestBody LoginBody b) { return accounts.login(b.email(), b.password()); }

    @GetMapping("/api/auth/me")
    public AccountService.Profile me() { return accounts.profile(Auth.userId()); }

    @PutMapping("/api/auth/me/preferences")
    public AppData.Prefs preferences(@Valid @RequestBody PrefsBody b) {
        return prefs.save(Auth.userId(), b.language(), b.unitSystem(), b.timeZone());
    }

    @PostMapping("/api/me/consent")
    public AppData.Consent grant(@Valid @RequestBody ConsentBody b) { return gdpr.grant(Auth.userId(), b.policyVersion()); }

    @DeleteMapping("/api/me/consent")
    public void revoke() { gdpr.revoke(Auth.userId()); }

    @PostMapping("/api/me/gdpr-requests")
    public AppData.GdprRequest createGdpr(@Valid @RequestBody GdprBody b) { return gdpr.create(Auth.userId(), b.type()); }

    @GetMapping("/api/me/gdpr-requests")
    public List<AppData.GdprRequest> myGdpr() { return gdpr.listFor(Auth.userId()); }

    @GetMapping(value = "/api/me/gdpr-requests/{id}/result", produces = MediaType.APPLICATION_JSON_VALUE)
    public String gdprResult(@PathVariable Integer id) { return gdpr.result(Auth.userId(), id); }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        EventService.Health h = events.health(prefs.get(0));
        return Map.of("api", h.api(), "database", h.database());
    }
}
