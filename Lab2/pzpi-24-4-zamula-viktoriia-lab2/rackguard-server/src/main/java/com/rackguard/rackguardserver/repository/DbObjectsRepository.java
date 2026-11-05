package com.rackguard.rackguardserver.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class DbObjectsRepository extends BaseRepository {
    public record EventBrief(Integer eventId, Integer doorId, String eventType, LocalDateTime eventTime) {}
    public record AnomalyRow(Integer eventId, String doorName, String roomName, String visitorName,
                             String eventType, LocalDateTime eventTime, String note) {}
    public record ActivePermission(Integer requestId, Integer userId, String visitorName, Integer roomId,
                                   String roomName, LocalDateTime startTime, LocalDateTime endTime) {}

    public DbObjectsRepository(JdbcTemplate jdbc) { super(jdbc); }

    public int totalApprovedByRoom(String roomName) {
        return jdbc.queryForObject("SELECT dbo.fn_TotalApprovedRequestsByRoom(?)", Integer.class, roomName);
    }

    public int activeUsersByRole(String role) {
        return jdbc.queryForObject("SELECT dbo.fn_CountActiveUsersByRole(?)", Integer.class, role);
    }

    public List<EventBrief> eventsAfterAverageTime() {
        return jdbc.query("SELECT * FROM dbo.fn_EventsAfterAverageTime()", (rs, n) -> new EventBrief(
                rs.getInt("EventId"), rs.getInt("DoorId"), rs.getString("EventType"), ts(rs, "EventTime")));
    }

    public List<String> categorizeEvents(Integer doorId) { return callProc("{call dbo.sp_CategorizeEventsByTime(?)}", doorId); }

    public List<String> safeInsertEvent(Integer doorId, String type) {
        return callProc("{call dbo.sp_SafeInsertDoorEvent(?, ?)}", doorId, type);
    }

    public List<String> fetchEverySecondEvent(Integer doorId) { return callProc("{call dbo.sp_FetchEverySecondEvent(?)}", doorId); }

    public List<String> deleteRejectedCursor(Integer roomId, int k) {
        return callProc("{call dbo.sp_DeleteRejectedRequests_Cursor(?, ?)}", roomId, k);
    }

    public List<AnomalyRow> anomalies(LocalDateTime from, LocalDateTime to) {
        return jdbc.query("SELECT * FROM dbo.vw_Anomalies WHERE EventTime >= ? AND EventTime < ? ORDER BY EventTime DESC",
                (rs, n) -> new AnomalyRow(rs.getInt("EventId"), rs.getString("DoorName"), rs.getString("RoomName"),
                        rs.getString("VisitorName"), rs.getString("EventType"), ts(rs, "EventTime"), rs.getString("Note")),
                from, to);
    }

    public List<ActivePermission> activePermissions() {
        return jdbc.query("SELECT * FROM dbo.vw_ActivePermissions ORDER BY EndTime", (rs, n) -> new ActivePermission(
                rs.getInt("RequestId"), rs.getInt("UserId"), rs.getString("VisitorName"), rs.getInt("RoomId"),
                rs.getString("RoomName"), ts(rs, "StartTime"), ts(rs, "EndTime")));
    }

    public boolean ping() {
        try {
            return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (Exception e) {
            return false;
        }
    }
}