package com.rackguard.rackguardserver.repository;

import com.rackguard.rackguardserver.model.Door;
import com.rackguard.rackguardserver.model.Room;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CatalogRepository extends BaseRepository {
    private static final RowMapper<Room> ROOM = (rs, n) ->
            new Room(rs.getInt("RoomId"), rs.getString("Name"), rs.getString("Description"));
    private static final RowMapper<Door> DOOR = (rs, n) -> new Door(
            rs.getInt("DoorId"), rs.getInt("RoomId"), rs.getString("Name"), rs.getString("Esp32DeviceId"));

    public CatalogRepository(JdbcTemplate jdbc) { super(jdbc); }


    public List<Room> findRooms() { return jdbc.query("SELECT * FROM dbo.Rooms ORDER BY RoomId", ROOM); }

    public Optional<Room> findRoom(Integer id) {
        return jdbc.query("SELECT * FROM dbo.Rooms WHERE RoomId = ?", ROOM, id).stream().findFirst();
    }


    public int insertRoom(String name, String description) {
        callProc("{call dbo.sp_InsertRoom(?, ?)}", name, description);
        return jdbc.queryForObject("SELECT TOP 1 RoomId FROM dbo.Rooms WHERE Name = ? ORDER BY RoomId DESC", Integer.class, name);
    }

    public void updateRoom(Integer id, String name, String description) {
        jdbc.update("UPDATE dbo.Rooms SET Name = ?, Description = ? WHERE RoomId = ?", name, description, id);
    }

    public void deleteRoom(Integer id) { jdbc.update("DELETE FROM dbo.Rooms WHERE RoomId = ?", id); }


    public List<Door> findDoors() { return jdbc.query("SELECT * FROM dbo.Doors ORDER BY DoorId", DOOR); }

    public List<Door> findDoorsByRoom(Integer roomId) {
        return jdbc.query("SELECT * FROM dbo.Doors WHERE RoomId = ?", DOOR, roomId);
    }

    public Optional<Door> findDoor(Integer id) {
        return jdbc.query("SELECT * FROM dbo.Doors WHERE DoorId = ?", DOOR, id).stream().findFirst();
    }

    public Optional<Door> findDoorByDevice(String deviceId) {
        return jdbc.query("SELECT * FROM dbo.Doors WHERE Esp32DeviceId = ?", DOOR, deviceId).stream().findFirst();
    }

    public int insertDoor(Integer roomId, String name, String deviceId) {
        return insertReturningId("INSERT INTO dbo.Doors (RoomId, Name, Esp32DeviceId) VALUES (?, ?, ?)",
                "DoorId", roomId, name, deviceId);
    }

    public void updateDoor(Integer id, Integer roomId, String name) {
        jdbc.update("UPDATE dbo.Doors SET RoomId = ?, Name = ? WHERE DoorId = ?", roomId, name, id);
    }

    public void linkDevice(Integer id, String deviceId) {
        jdbc.update("UPDATE dbo.Doors SET Esp32DeviceId = ? WHERE DoorId = ?", deviceId, id);
    }

    public void clearDevice(Integer id) { callProc("{call dbo.sp_ClearDoorDevice(?)}", id); }

    public void deleteDoor(Integer id) { jdbc.update("DELETE FROM dbo.Doors WHERE DoorId = ?", id); }
}

