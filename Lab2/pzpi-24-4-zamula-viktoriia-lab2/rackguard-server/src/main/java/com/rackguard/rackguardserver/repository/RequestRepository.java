package com.rackguard.rackguardserver.repository;

import com.rackguard.rackguardserver.model.AccessRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RequestRepository extends BaseRepository {
    private static final String LIVE = "('Created','Approved','ConfirmedLocally')";
    private static final RowMapper<AccessRequest> MAPPER = (rs, n) -> new AccessRequest(
            rs.getInt("RequestId"), rs.getInt("UserId"), rs.getInt("RoomId"), intOrNull(rs, "ApproverId"),
            rs.getString("Purpose"), ts(rs, "StartTime"), ts(rs, "EndTime"), rs.getString("Status"));

    public RequestRepository(JdbcTemplate jdbc) { super(jdbc); }

    public Optional<AccessRequest> findById(Integer id) {
        return jdbc.query("SELECT * FROM dbo.AccessRequests WHERE RequestId = ?", MAPPER, id).stream().findFirst();
    }

    public List<AccessRequest> findByUser(Integer userId) {
        return jdbc.query("SELECT * FROM dbo.AccessRequests WHERE UserId = ? ORDER BY StartTime DESC", MAPPER, userId);
    }

    public List<AccessRequest> findByStatus(String status) {
        return status == null
                ? jdbc.query("SELECT * FROM dbo.AccessRequests ORDER BY StartTime", MAPPER)
                : jdbc.query("SELECT * FROM dbo.AccessRequests WHERE Status = ? ORDER BY StartTime", MAPPER, status);
    }

    public Integer insertViaProc(Integer userId, Integer roomId, String purpose, LocalDateTime start, LocalDateTime end) {
        callProc("{call dbo.sp_InsertAccessRequest(?, ?, ?, ?, ?)}", userId, roomId, purpose, start, end);
        return jdbc.query("SELECT TOP 1 RequestId FROM dbo.AccessRequests WHERE UserId = ? AND RoomId = ? "
                        + "AND StartTime = ? AND EndTime = ? ORDER BY RequestId DESC",
                (rs, n) -> rs.getInt(1), userId, roomId, start, end).stream().findFirst().orElse(null);
    }

    public List<AccessRequest> findOverlapping(Integer userId, LocalDateTime start, LocalDateTime end) {
        return jdbc.query("SELECT * FROM dbo.AccessRequests WHERE UserId = ? AND Status IN " + LIVE
                + " AND StartTime < ? AND EndTime > ?", MAPPER, userId, end, start);
    }

    public List<AccessRequest> findByRoomBetween(Integer roomId, LocalDateTime from, LocalDateTime to) {
        return jdbc.query("SELECT * FROM dbo.AccessRequests WHERE RoomId = ? AND Status IN " + LIVE
                + " AND StartTime < ? AND EndTime > ? ORDER BY StartTime", MAPPER, roomId, to, from);
    }

    public List<AccessRequest> findBetween(LocalDateTime from, LocalDateTime to) {
        return jdbc.query("SELECT * FROM dbo.AccessRequests WHERE StartTime >= ? AND StartTime < ?", MAPPER, from, to);
    }

    public List<AccessRequest> findDueForExpiry(LocalDateTime now) {
        return jdbc.query("SELECT * FROM dbo.AccessRequests WHERE Status = 'Created' AND StartTime <= ?", MAPPER, now);
    }

    public List<AccessRequest> findDueForCompletion(LocalDateTime now) {
        return jdbc.query("SELECT * FROM dbo.AccessRequests WHERE Status IN ('Approved','ConfirmedLocally') "
                + "AND EndTime < ?", MAPPER, now);
    }

    public Optional<Integer> findActiveApproved(Integer userId, Integer roomId) {
        return jdbc.query("SELECT TOP 1 RequestId FROM dbo.vw_ActivePermissions WHERE UserId = ? AND RoomId = ?",
                (rs, n) -> rs.getInt(1), userId, roomId).stream().findFirst();
    }

    public Optional<Integer> findActiveConfirmed(Integer userId, Integer roomId, LocalDateTime now) {
        return jdbc.query("SELECT TOP 1 RequestId FROM dbo.AccessRequests WHERE UserId = ? AND RoomId = ? "
                        + "AND Status = 'ConfirmedLocally' AND ? BETWEEN StartTime AND EndTime",
                (rs, n) -> rs.getInt(1), userId, roomId, now).stream().findFirst();
    }

    public void updateStatus(Integer id, String status) {
        jdbc.update("UPDATE dbo.AccessRequests SET Status = ? WHERE RequestId = ?", status, id);
    }

    public void decide(Integer id, Integer approverId, String status) {
        jdbc.update("UPDATE dbo.AccessRequests SET Status = ?, ApproverId = ? WHERE RequestId = ?", status, approverId, id);
    }
}
