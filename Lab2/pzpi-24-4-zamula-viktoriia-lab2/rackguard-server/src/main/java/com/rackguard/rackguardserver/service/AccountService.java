package com.rackguard.rackguardserver.service;

import com.rackguard.rackguardserver.exception.ApiException;
import com.rackguard.rackguardserver.model.User;
import com.rackguard.rackguardserver.repository.UserRepository;
import com.rackguard.rackguardserver.security.JwtService;
import com.rackguard.rackguardserver.store.AppData;
import com.rackguard.rackguardserver.store.DataStore;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountService {
    public record LoginResult(String token, long expiresInSeconds, UserService.View user, AppData.Prefs prefs) {}
    public record Profile(UserService.View user, AppData.Prefs prefs, AppData.Consent consent) {}
    private record Attempt(int failures, Instant lockedUntil) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final PrefsService prefs;
    private final UserService userService;
    private final DataStore store;

    public AccountService(UserRepository users, PasswordEncoder encoder, JwtService jwt, PrefsService prefs,
                          UserService userService, DataStore store) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.prefs = prefs;
        this.userService = userService;
        this.store = store;
    }

    public LoginResult login(String email, String password) {
        String key = email.toLowerCase();
        Attempt a = attempts.get(key);
        if (a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now())) {
            long minutes = Duration.between(Instant.now(), a.lockedUntil()).toMinutes() + 1;
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "auth.locked", minutes);
        }
        User u = users.findByEmail(email).orElse(null);
        if (u == null || !encoder.matches(password, u.passwordHash())) {
            attempts.merge(key, new Attempt(1, null), (old, n) -> {
                int failures = old.lockedUntil() != null ? 1 : old.failures() + 1;
                return new Attempt(failures, failures >= 5 ? Instant.now().plus(Duration.ofMinutes(15)) : null);
            });
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid");
        }
        if (!u.isActive()) throw ApiException.forbidden("auth.blocked");
        attempts.remove(key);
        return new LoginResult(jwt.generate(u.userId(), u.role()), jwt.ttlSeconds(),
                userService.view(u), prefs.get(u.userId()));
    }

    public Profile profile(Integer userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("user.notFound"));
        AppData.Consent consent = store.read(d -> d.consents.stream()
                .filter(c -> c.userId().equals(userId)).reduce((x, y) -> y).orElse(null));
        return new Profile(userService.view(u), prefs.get(userId), consent);
    }
}
