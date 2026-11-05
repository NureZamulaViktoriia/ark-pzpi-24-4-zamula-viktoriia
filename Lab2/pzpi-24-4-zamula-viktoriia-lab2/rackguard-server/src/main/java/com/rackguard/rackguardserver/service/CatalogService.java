package com.rackguard.rackguardserver.service;


import com.rackguard.rackguardserver.exception.ApiException;
import com.rackguard.rackguardserver.model.Door;
import com.rackguard.rackguardserver.model.Room;
import com.rackguard.rackguardserver.repository.CatalogRepository;
import com.rackguard.rackguardserver.store.DataStore;
import com.rackguard.rackguardserver.util.Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogService {
    public record QrView(Integer doorId, String payload) {}
    public record SecretView(String deviceId, String secret) {}

    private final CatalogRepository repo;
    private final DataStore store;
    private final String qrSecret;
    private final String deviceMaster;

    public CatalogService(CatalogRepository repo, DataStore store, @Value("${app.qr.secret}") String qrSecret,
                          @Value("${app.device.master-secret}") String deviceMaster) {
        this.repo = repo;
        this.store = store;
        this.qrSecret = qrSecret;
        this.deviceMaster = deviceMaster;
    }

    private boolean archived(String kind, Integer id) { return store.read(d -> d.archived.contains(kind + ":" + id)); }

    public List<Room> rooms(boolean includeArchived) {
        return repo.findRooms().stream().filter(r -> includeArchived || !archived("ROOM", r.roomId())).toList();
    }

    public Room requireActiveRoom(Integer id) {
        return repo.findRoom(id).filter(r -> !archived("ROOM", id)).orElseThrow(() -> ApiException.notFound("room.notFound"));
    }

    public Room createRoom(String name, String description, Integer actorId) {
        int id = repo.insertRoom(name, description);
        store.audit(actorId, "ROOM_CREATE", "room:" + id);
        return repo.findRoom(id).orElseThrow();
    }

    public Room updateRoom(Integer id, String name, String description, Integer actorId) {
        requireActiveRoom(id);
        repo.updateRoom(id, name, description);
        store.audit(actorId, "ROOM_UPDATE", "room:" + id);
        return repo.findRoom(id).orElseThrow();
    }

    public void archiveRoom(Integer id, Integer actorId) {
        requireActiveRoom(id);
        store.write(d -> d.archived.add("ROOM:" + id));
        store.audit(actorId, "ROOM_ARCHIVE", "room:" + id);
    }

    public void deleteRoom(Integer id, Integer actorId) {
        repo.findRoom(id).orElseThrow(() -> ApiException.notFound("room.notFound"));
        repo.deleteRoom(id);
        store.audit(actorId, "ROOM_DELETE", "room:" + id);
    }

    public List<Door> doors(boolean includeArchived) {
        return repo.findDoors().stream().filter(d -> includeArchived || !archived("DOOR", d.doorId())).toList();
    }

    public Door requireDoor(Integer id) { return repo.findDoor(id).orElseThrow(() -> ApiException.notFound("door.notFound")); }

    private void checkDeviceFree(String deviceId, Integer exceptDoorId) {
        if (deviceId == null || deviceId.isBlank()) return;
        repo.findDoorByDevice(deviceId).filter(d -> !d.doorId().equals(exceptDoorId))
                .ifPresent(d -> { throw ApiException.conflict("door.deviceTaken"); });
    }

    public Door createDoor(Integer roomId, String name, String deviceId, Integer actorId) {
        requireActiveRoom(roomId);
        checkDeviceFree(deviceId, null);
        int id = repo.insertDoor(roomId, name, deviceId == null || deviceId.isBlank() ? null : deviceId);
        store.audit(actorId, "DOOR_CREATE", "door:" + id);
        return requireDoor(id);
    }

    public Door updateDoor(Integer id, Integer roomId, String name, Integer actorId) {
        requireDoor(id);
        requireActiveRoom(roomId);
        repo.updateDoor(id, roomId, name);
        store.audit(actorId, "DOOR_UPDATE", "door:" + id);
        return requireDoor(id);
    }

    public Door linkDevice(Integer id, String deviceId, Integer actorId) {
        requireDoor(id);
        checkDeviceFree(deviceId, id);
        repo.linkDevice(id, deviceId);
        store.audit(actorId, "DOOR_LINK_DEVICE", "door:" + id);
        return requireDoor(id);
    }

    public Door unlinkDevice(Integer id, Integer actorId) {
        requireDoor(id);
        repo.clearDevice(id);
        store.audit(actorId, "DOOR_UNLINK_DEVICE", "door:" + id);
        return requireDoor(id);
    }

    public void archiveDoor(Integer id, Integer actorId) {
        requireDoor(id);
        store.write(d -> d.archived.add("DOOR:" + id));
        store.audit(actorId, "DOOR_ARCHIVE", "door:" + id);
    }

    public void deleteDoor(Integer id, Integer actorId) {
        requireDoor(id);
        repo.deleteDoor(id);
        store.audit(actorId, "DOOR_DELETE", "door:" + id);
    }

    public boolean hasDoor(Integer roomId) { return !repo.findDoorsByRoom(roomId).isEmpty(); }

    private String qrSignature(Integer doorId) { return Util.hmacHex(qrSecret, "door:" + doorId).substring(0, 32); }

    public QrView qr(Integer doorId) {
        requireDoor(doorId);
        return new QrView(doorId, "RG1|" + doorId + "|" + qrSignature(doorId));
    }

    public Integer verifyQr(String payload) {
        String[] p = payload == null ? new String[0] : payload.split("\\|");
        try {
            if (p.length == 3 && "RG1".equals(p[0]) && Util.safeEquals(qrSignature(Integer.valueOf(p[1])), p[2])) {
                return Integer.valueOf(p[1]);
            }
        } catch (NumberFormatException e) {
        }
        throw ApiException.bad("visit.badQr");
    }

    public String deviceSecret(String deviceId) { return Util.hmacHex(deviceMaster, "device:" + deviceId); }

    public SecretView secretOf(Integer doorId, Integer actorId) {
        Door d = requireDoor(doorId);
        if (d.esp32DeviceId() == null) throw ApiException.conflict("visit.notAllowed");
        store.audit(actorId, "DEVICE_SECRET_VIEW", "door:" + doorId);
        return new SecretView(d.esp32DeviceId(), deviceSecret(d.esp32DeviceId()));
    }
}