package com.rackguard.rackguardserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rackguard.rackguardserver.exception.ApiException;
import com.rackguard.rackguardserver.model.User;
import com.rackguard.rackguardserver.repository.EventRepository;
import com.rackguard.rackguardserver.repository.RequestRepository;
import com.rackguard.rackguardserver.repository.UserRepository;
import com.rackguard.rackguardserver.store.AppData;
import com.rackguard.rackguardserver.store.AppData.GdprRequest;
import com.rackguard.rackguardserver.store.DataStore;
import com.rackguard.rackguardserver.util.Const;
import com.rackguard.rackguardserver.util.Util;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GdprService {
    private final DataStore store;
    private final UserRepository users;
    private final RequestRepository requests;
    private final EventRepository events;
    private final PrefsService prefs;

    public GdprService(DataStore store, UserRepository users, RequestRepository requests, EventRepository events,
                       PrefsService prefs) {
        this.store = store;
        this.users = users;
        this.requests = requests;
        this.events = events;
        this.prefs = prefs;
    }


    public AppData.Consent grant(Integer userId, String policyVersion) {
        AppData.Consent c = new AppData.Consent(userId, policyVersion, Util.nowUtc(), null);
        store.write(d -> d.consents.add(c));
        return c;
    }

    public void revoke(Integer userId) {
        store.write(d -> d.consents.replaceAll(c -> c.userId().equals(userId) && c.revokedAt() == null
                ? new AppData.Consent(c.userId(), c.policyVersion(), c.grantedAt(), Util.nowUtc()) : c));
    }


    public GdprRequest create(Integer userId, String type) {
        String t = type.toUpperCase();
        if (!Set.of("EXPORT", "DELETE").contains(t)) throw ApiException.bad("settings.invalid");
        return store.update(d -> {
            int id = d.gdpr.stream().mapToInt(GdprRequest::requestId).max().orElse(0) + 1;
            GdprRequest r = new GdprRequest(id, userId, t, "PENDING", Util.nowUtc(), null);
            d.gdpr.add(r);
            return r;
        });
    }

    private static GdprRequest withoutResult(GdprRequest r) {
        return new GdprRequest(r.requestId(), r.userId(), r.type(), r.status(), r.createdAt(), null);
    }

    public List<GdprRequest> listFor(Integer userId) {
        return store.read(d -> d.gdpr.stream().filter(r -> r.userId().equals(userId)).map(GdprService::withoutResult).toList());
    }

    public List<GdprRequest> listAll() { return store.read(d -> d.gdpr.stream().map(GdprService::withoutResult).toList()); }

    private GdprRequest find(Integer id) {
        return store.read(d -> d.gdpr.stream().filter(r -> r.requestId().equals(id)).findFirst())
                .orElseThrow(() -> ApiException.notFound("error.notFound"));
    }


    public String result(Integer userId, Integer id) {
        GdprRequest r = find(id);
        if (!r.userId().equals(userId) || r.resultJson() == null) throw ApiException.notFound("error.notFound");
        return r.resultJson();
    }

    public GdprRequest execute(Integer adminId, Integer id) {
        GdprRequest r = find(id);
        if (!"PENDING".equals(r.status())) throw ApiException.conflict("request.badStatus", r.status());
        String json = null;
        if ("EXPORT".equals(r.type())) {
            json = export(r.userId());
            store.audit(adminId, "PERSONAL_DATA_EXPORT", "user:" + r.userId());
        } else {
            User u = users.findById(r.userId()).orElseThrow(() -> ApiException.notFound("user.notFound"));
            if (Const.ADMIN.equals(u.role()) && users.countActiveByRole(Const.ADMIN) <= 1) {
                throw ApiException.conflict("gdpr.adminForbidden");
            }
            users.anonymize(r.userId());
            store.audit(adminId, "PERSONAL_DATA_ANONYMIZE", "user:" + r.userId());
        }
        String result = json;
        store.write(d -> d.gdpr.replaceAll(x -> x.requestId().equals(id)
                ? new GdprRequest(x.requestId(), x.userId(), x.type(), "DONE", x.createdAt(), result) : x));
        return withoutResult(find(id));
    }

    private String export(Integer userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("user.notFound"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", Map.of("userId", u.userId(), "name", u.name(), "email", u.email(), "role", u.role(),
                "active", u.isActive(), "hasRfidTag", u.rfidTag() != null));
        data.put("preferences", prefs.get(userId));
        data.put("consents", store.read(d -> d.consents.stream().filter(c -> c.userId().equals(userId)).toList()));
        data.put("accessRequests", requests.findByUser(userId));
        data.put("doorEvents", events.findByUser(userId));
        try {
            return Util.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Export failed", e);
        }
    }
}