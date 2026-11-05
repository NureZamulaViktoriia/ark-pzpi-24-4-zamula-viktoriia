package com.rackguard.rackguardserver.service;


import com.rackguard.rackguardserver.exception.ApiException;
import com.rackguard.rackguardserver.store.AppData.Prefs;
import com.rackguard.rackguardserver.store.DataStore;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

@Service
public class PrefsService {
    private static final DateTimeFormatter UK = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter EN = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a", Locale.ENGLISH);
    private final DataStore store;

    public PrefsService(DataStore store) { this.store = store; }

    public Prefs get(Integer userId) {
        return store.read(d -> d.prefs.stream().filter(p -> p.userId().equals(userId)).findFirst()
                .orElse(new Prefs(userId, "en", "METRIC", "UTC")));
    }

    public Prefs save(Integer userId, String language, String unitSystem, String timeZone) {
        if (!Set.of("uk", "en").contains(language) || !Set.of("METRIC", "IMPERIAL").contains(unitSystem)) {
            throw ApiException.bad("settings.invalid");
        }
        try { ZoneId.of(timeZone); } catch (DateTimeException e) { throw ApiException.bad("settings.invalid"); }
        Prefs p = new Prefs(userId, language, unitSystem, timeZone);
        store.write(d -> { d.prefs.removeIf(x -> x.userId().equals(userId)); d.prefs.add(p); });
        return p;
    }

    public OffsetDateTime toOffset(LocalDateTime utc, Prefs p) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of(p.timeZone())).toOffsetDateTime();
    }

    public String display(LocalDateTime utc, Prefs p) {
        OffsetDateTime t = toOffset(utc, p);
        return t == null ? null : t.format("uk".equals(p.language()) ? UK : EN);
    }

    public double temperature(double celsius, Prefs p) {
        return "IMPERIAL".equals(p.unitSystem()) ? Math.round((celsius * 9 / 5 + 32) * 10) / 10.0 : celsius;
    }

    public String temperatureUnit(Prefs p) { return "IMPERIAL".equals(p.unitSystem()) ? "°F" : "°C"; }
}
