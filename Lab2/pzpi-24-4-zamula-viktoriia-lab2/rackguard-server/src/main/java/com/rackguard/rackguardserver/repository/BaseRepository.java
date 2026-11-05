package com.rackguard.rackguardserver.repository;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;


public abstract class BaseRepository {
    protected final JdbcTemplate jdbc;

    protected BaseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    protected int insertReturningId(String sql, String idColumn, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{idColumn});
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, holder);
        return Objects.requireNonNull(holder.getKey()).intValue();
    }


    protected List<String> callProc(String call, Object... args) {
        return jdbc.execute((ConnectionCallback<List<String>>) con -> {
            try (CallableStatement cs = con.prepareCall(call)) {
                for (int i = 0; i < args.length; i++) cs.setObject(i + 1, args[i]);
                boolean hasResult = cs.execute();
                while (hasResult || cs.getUpdateCount() != -1) hasResult = cs.getMoreResults();
                List<String> messages = new ArrayList<>();
                for (SQLWarning w = cs.getWarnings(); w != null; w = w.getNextWarning()) messages.add(w.getMessage());
                return messages;
            }
        });
    }

    protected static LocalDateTime ts(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, LocalDateTime.class);
    }

    protected static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
