package com.rackguard.rackguardserver.service;

import com.rackguard.rackguardserver.exception.ApiException;
import com.rackguard.rackguardserver.model.User;
import com.rackguard.rackguardserver.repository.UserRepository;
import com.rackguard.rackguardserver.store.DataStore;
import com.rackguard.rackguardserver.util.Const;
import com.rackguard.rackguardserver.util.Util;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserService {
    public record View(Integer userId, String name, String email, String role,
                       boolean hasRfid, boolean active, boolean archived) {}
    public record ImportReport(int imported, List<String> errors) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PrefsService prefs;
    private final DataStore store;

    public UserService(UserRepository users, PasswordEncoder encoder, PrefsService prefs, DataStore store) {
        this.users = users;
        this.encoder = encoder;
        this.prefs = prefs;
        this.store = store;
    }

    public View view(User u) {
        return new View(u.userId(), u.name(), u.email(), u.role(), u.rfidTag() != null, u.isActive(),
                store.read(d -> d.archived.contains("USER:" + u.userId())));
    }

    public List<View> list() { return users.findAll().stream().map(this::view).toList(); }

    public View get(Integer id) { return view(require(id)); }

    private User require(Integer id) { return users.findById(id).orElseThrow(() -> ApiException.notFound("user.notFound")); }

    private static String rfidHash(String uid) { return uid == null || uid.isBlank() ? null : Util.uidHash(uid); }

    public View create(String name, String email, String role, String language, String password,
                       String rfidUid, Integer actorId) {
        if (!Set.of(Const.ADMIN, Const.VISITOR).contains(role)) throw ApiException.bad("user.invalidRole");
        if (password == null || password.length() < 8) throw ApiException.bad("user.weakPassword");
        if (users.findByEmail(email).isPresent()) throw ApiException.conflict("user.exists");
        int id = users.insert(new User(null, name, email, encoder.encode(password), role, rfidHash(rfidUid), true));
        prefs.save(id, language == null ? "en" : language, "METRIC", "UTC");
        store.audit(actorId, "USER_CREATE", "user:" + id);
        return get(id);
    }

    public View update(Integer id, String name, String email, String rfidUid, Integer actorId) {
        User u = require(id);
        users.update(id, name, email, rfidUid == null || rfidUid.isBlank() ? u.rfidTag() : rfidHash(rfidUid));
        store.audit(actorId, "USER_UPDATE", "user:" + id);
        return get(id);
    }

    private boolean isLastAdmin(User u) { return Const.ADMIN.equals(u.role()) && users.countActiveByRole(Const.ADMIN) <= 1; }

    public View changeRole(Integer id, String role, Integer actorId) {
        if (!Set.of(Const.ADMIN, Const.VISITOR).contains(role)) throw ApiException.bad("user.invalidRole");
        User u = require(id);
        if (!role.equals(u.role()) && isLastAdmin(u)) throw ApiException.conflict("user.lastAdmin");
        users.updateRole(id, role);
        store.audit(actorId, "ROLE_CHANGE", "user:" + id + " " + u.role() + "->" + role);
        return get(id);
    }

    public View setActive(Integer id, boolean active, Integer actorId) {
        User u = require(id);
        if (!active && isLastAdmin(u)) throw ApiException.conflict("user.lastAdmin");
        users.toggleStatus(id, active);
        store.audit(actorId, active ? "USER_UNBLOCK" : "USER_BLOCK", "user:" + id);
        return get(id);
    }

    public View archive(Integer id, Integer actorId) {
        setActive(id, false, actorId);
        store.write(d -> d.archived.add("USER:" + id));
        store.audit(actorId, "USER_ARCHIVE", "user:" + id);
        return get(id);
    }

    public void delete(Integer id, Integer actorId) {
        require(id);
        users.delete(id);
        store.audit(actorId, "USER_DELETE", "user:" + id);
    }

    public ImportReport importCsv(String text, Integer actorId) {
        int imported = 0;
        List<String> errors = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank() || (i == 0 && lines[i].toLowerCase().startsWith("name"))) continue;
            String[] c = lines[i].split(",", -1);
            try {
                if (c.length < 5) throw new IllegalArgumentException("expected 5-6 columns");
                create(c[0].trim(), c[1].trim(), c[2].trim(), c[3].trim(), c[4].trim(), c.length > 5 ? c[5] : null, actorId);
                imported++;
            } catch (ApiException e) {
                errors.add("line " + (i + 1) + ": " + e.code());
            } catch (RuntimeException e) {
                errors.add("line " + (i + 1) + ": " + e.getMessage());
            }
        }
        store.audit(actorId, "USER_IMPORT", "imported=" + imported + ", errors=" + errors.size());
        return new ImportReport(imported, errors);
    }

    public Util.FileData export(String format, Integer actorId) {
        store.audit(actorId, "USERS_EXPORT", format);
        List<List<Object>> rows = list().stream().<List<Object>>map(u -> List.of(u.userId(), u.name(), u.email(),
                u.role(), u.active(), u.archived())).toList();
        return Util.export(format, "users", List.of("userId", "name", "email", "role", "active", "archived"), rows);
    }
}
