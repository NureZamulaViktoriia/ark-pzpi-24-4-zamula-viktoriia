package com.rackguard.rackguardserver.controller;

import com.rackguard.rackguardserver.model.Door;
import com.rackguard.rackguardserver.model.Room;
import com.rackguard.rackguardserver.security.Auth;
import com.rackguard.rackguardserver.service.CatalogService;
import com.rackguard.rackguardserver.service.UserService;
import com.rackguard.rackguardserver.util.Util;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin: users, rooms, doors")
public class AdminCatalogController {
    public record UserBody(@NotBlank String name, @NotBlank @Email String email, @NotBlank String role,
                           String language, @NotBlank String password, String rfidUid) {}
    public record UserUpdateBody(@NotBlank String name, @NotBlank @Email String email, String rfidUid) {}
    public record RoleBody(@NotBlank String role) {}
    public record ActiveBody(boolean active) {}
    public record RoomBody(@NotBlank String name, String description) {}
    public record DoorBody(@NotNull Integer roomId, @NotBlank String name, String deviceId) {}
    public record DoorUpdateBody(@NotNull Integer roomId, @NotBlank String name) {}
    public record DeviceBody(@NotBlank String deviceId) {}

    private final UserService users;
    private final CatalogService catalog;

    public AdminCatalogController(UserService users, CatalogService catalog) {
        this.users = users;
        this.catalog = catalog;
    }


    @GetMapping("/users") public List<UserService.View> users() { return users.list(); }
    @GetMapping("/users/{id}") public UserService.View user(@PathVariable Integer id) { return users.get(id); }

    @PostMapping("/users")
    public UserService.View createUser(@Valid @RequestBody UserBody b) {
        return users.create(b.name(), b.email(), b.role(), b.language(), b.password(), b.rfidUid(), Auth.userId());
    }

    @PutMapping("/users/{id}")
    public UserService.View updateUser(@PathVariable Integer id, @Valid @RequestBody UserUpdateBody b) {
        return users.update(id, b.name(), b.email(), b.rfidUid(), Auth.userId());
    }

    @PutMapping("/users/{id}/role")
    public UserService.View role(@PathVariable Integer id, @Valid @RequestBody RoleBody b) { return users.changeRole(id, b.role(), Auth.userId()); }

    @PutMapping("/users/{id}/status")
    public UserService.View status(@PathVariable Integer id, @RequestBody ActiveBody b) { return users.setActive(id, b.active(), Auth.userId()); }

    @PostMapping("/users/{id}/archive")
    public UserService.View archive(@PathVariable Integer id) { return users.archive(id, Auth.userId()); }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Integer id) { users.delete(id, Auth.userId()); }

    @PostMapping(value = "/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserService.ImportReport importUsers(@RequestPart("file") MultipartFile file) throws IOException {
        return users.importCsv(new String(file.getBytes(), StandardCharsets.UTF_8), Auth.userId());
    }

    @GetMapping("/users/export")
    public ResponseEntity<byte[]> exportUsers(@RequestParam(defaultValue = "csv") String format) {
        return download(users.export(format, Auth.userId()));
    }

    static ResponseEntity<byte[]> download(Util.FileData f) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + f.fileName())
                .contentType(MediaType.parseMediaType(f.contentType())).body(f.data());
    }


    @GetMapping("/rooms")
    public List<Room> rooms(@RequestParam(defaultValue = "false") boolean includeArchived) { return catalog.rooms(includeArchived); }

    @PostMapping("/rooms")
    public Room createRoom(@Valid @RequestBody RoomBody b) { return catalog.createRoom(b.name(), b.description(), Auth.userId()); }

    @PutMapping("/rooms/{id}")
    public Room updateRoom(@PathVariable Integer id, @Valid @RequestBody RoomBody b) {
        return catalog.updateRoom(id, b.name(), b.description(), Auth.userId());
    }

    @PostMapping("/rooms/{id}/archive") public void archiveRoom(@PathVariable Integer id) { catalog.archiveRoom(id, Auth.userId()); }
    @DeleteMapping("/rooms/{id}") public void deleteRoom(@PathVariable Integer id) { catalog.deleteRoom(id, Auth.userId()); }


    @GetMapping("/doors")
    public List<Door> doors(@RequestParam(defaultValue = "false") boolean includeArchived) { return catalog.doors(includeArchived); }

    @PostMapping("/doors")
    public Door createDoor(@Valid @RequestBody DoorBody b) { return catalog.createDoor(b.roomId(), b.name(), b.deviceId(), Auth.userId()); }

    @PutMapping("/doors/{id}")
    public Door updateDoor(@PathVariable Integer id, @Valid @RequestBody DoorUpdateBody b) {
        return catalog.updateDoor(id, b.roomId(), b.name(), Auth.userId());
    }

    @PutMapping("/doors/{id}/device")
    public Door link(@PathVariable Integer id, @Valid @RequestBody DeviceBody b) { return catalog.linkDevice(id, b.deviceId(), Auth.userId()); }

    @DeleteMapping("/doors/{id}/device")
    public Door unlink(@PathVariable Integer id) { return catalog.unlinkDevice(id, Auth.userId()); }

    @PostMapping("/doors/{id}/archive") public void archiveDoor(@PathVariable Integer id) { catalog.archiveDoor(id, Auth.userId()); }
    @DeleteMapping("/doors/{id}") public void deleteDoor(@PathVariable Integer id) { catalog.deleteDoor(id, Auth.userId()); }

    @GetMapping("/doors/{id}/qr")
    public CatalogService.QrView qr(@PathVariable Integer id) { return catalog.qr(id); }

    @GetMapping("/doors/{id}/device-secret")
    public CatalogService.SecretView secret(@PathVariable Integer id) { return catalog.secretOf(id, Auth.userId()); }
}