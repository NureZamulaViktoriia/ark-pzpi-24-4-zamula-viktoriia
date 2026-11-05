package com.rackguard.rackguardserver.repository;

import com.rackguard.rackguardserver.model.DoorEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class EventRepository extends BaseRepository {
    private static final RowMapper<DoorEvent> MAPPER = (rs, n) -> new DoorEvent(
            rs.getInt("EventId"), rs.getInt("DoorId"), intOrNull(rs, "UserId"), intOrNull(rs, "RequestId"),
            rs.getString("EventType"), ts(rs, "EventTime"), rs.getBoolean("IsAnomaly"), rs.getString("Note"));

    public record Filter(LocalDateTime from, LocalDateTime to, Integer doorId, Integer userId, String type) {}

    public record Visit(Integer requestId, Integer userId, Integer roomId, LocalDateTime firstIn, LocalDateTime lastOut) {}

    public EventRepository(JdbcTemplate jdbc) { super(jdbc); }

    public int insert(DoorEvent e) {
        return insertReturningId("INSERT INTO dbo.DoorEvents (DoorId, UserId, RequestId, EventType, EventTime, "
                        + "IsAnomaly, Note) VALUES (?, ?, ?, ?, ?, ?, ?)", "EventId",
                e.doorId(), e.userId(), e.requestId(), e.eventType(), e.eventTime(), e.isAnomaly(), e.note());
    }

    private String where(Filter f, List<Object> args) {
        StringBuilder sb = new StringBuilder(" WHERE 1 = 1");
        if (f.from() != null) { sb.append(" AND EventTime >= ?"); args.add(f.from()); }
        if (f.to() != null) { sb.append(" AND EventTime < ?"); args.add(f.to()); }
        if (f.doorId() != null) { sb.append(" AND DoorId = ?"); args.add(f.doorId()); }
        if (f.userId() != null) { sb.append(" AND UserId = ?"); args.add(f.userId()); }
        if (f.type() != null) { sb.append(" AND EventType = ?"); args.add(f.type()); }
        return sb.toString();
    }

    public List<DoorEvent> findPage(Filter f, int page, int size) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT * FROM dbo.SecurityLogs" + where(f, args)
                + " ORDER BY EventTime DESC, EventId DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        args.add(page * size);
        args.add(size);
        return jdbc.query(sql, MAPPER, args.toArray());
    }

    public long count(Filter f) {
        List<Object> args = new ArrayList<>();
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.SecurityLogs" + where(f, args), Long.class, args.toArray());
    }

    public List<DoorEvent> find(Filter f) {
        List<Object> args = new ArrayList<>();
        return jdbc.query("SELECT * FROM dbo.SecurityLogs" + where(f, args) + " ORDER BY EventTime, EventId",
                MAPPER, args.toArray());
    }

    public List<DoorEvent> findByUser(Integer userId) {
        return jdbc.query("SELECT * FROM dbo.DoorEvents WHERE UserId = ? ORDER BY EventTime", MAPPER, userId);
    }

    public Optional<DoorEvent> findLastAuthorization(Integer doorId, LocalDateTime time, int seconds) {
        return jdbc.query("SELECT TOP 1 * FROM dbo.DoorEvents WHERE DoorId = ? "
                        + "AND EventType IN ('CommandIssued','RfidGranted','ExitButton') "
                        + "AND EventTime <= ? AND EventTime >= DATEADD(SECOND, ?, ?) ORDER BY EventTime DESC",
                MAPPER, doorId, time, -seconds, time).stream().findFirst();
    }

    public Optional<DoorEvent> findLastOpened(Integer doorId, LocalDateTime time) {
        return jdbc.query("SELECT TOP 1 * FROM dbo.DoorEvents WHERE DoorId = ? AND EventType = 'DoorOpened' "
                + "AND EventTime <= ? ORDER BY EventTime DESC", MAPPER, doorId, time).stream().findFirst();
    }

    public List<Visit> findVisits(LocalDateTime from, LocalDateTime to) {
        return jdbc.query("SELECT e.RequestId, e.UserId, d.RoomId, "
                        + "MIN(CASE WHEN e.EventType = 'DoorOpened' THEN e.EventTime END) AS FirstIn, "
                        + "MAX(CASE WHEN e.EventType = 'DoorClosed' THEN e.EventTime END) AS LastOut "
                        + "FROM dbo.SecurityLogs e INNER JOIN dbo.Doors d ON d.DoorId = e.DoorId "
                        + "WHERE e.RequestId IS NOT NULL AND e.EventTime >= ? AND e.EventTime < ? "
                        + "GROUP BY e.RequestId, e.UserId, d.RoomId "
                        + "HAVING MIN(CASE WHEN e.EventType = 'DoorOpened' THEN e.EventTime END) IS NOT NULL",
                (rs, n) -> new Visit(rs.getInt("RequestId"), intOrNull(rs, "UserId"), rs.getInt("RoomId"),
                        ts(rs, "FirstIn"), ts(rs, "LastOut")), from, to);
    }

    public int anonymizeOlderThan(LocalDateTime cutoff) {
        return jdbc.update("UPDATE dbo.DoorEvents SET UserId = NULL WHERE EventTime < ? AND UserId IS NOT NULL", cutoff);
    }
}
