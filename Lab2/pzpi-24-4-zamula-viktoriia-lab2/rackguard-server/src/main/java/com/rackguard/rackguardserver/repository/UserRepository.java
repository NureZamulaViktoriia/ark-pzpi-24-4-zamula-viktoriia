package com.rackguard.rackguardserver.repository;

import com.rackguard.rackguardserver.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository extends BaseRepository {
    private static final RowMapper<User> MAPPER = (rs, n) -> new User(
            rs.getInt("UserId"), rs.getString("Name"), rs.getString("Email"), rs.getString("PasswordHash"),
            rs.getString("Role"), rs.getString("RfidTag"), rs.getBoolean("IsActive"));

    public UserRepository(JdbcTemplate jdbc) { super(jdbc); }

    public List<User> findAll() { return jdbc.query("SELECT * FROM dbo.Users ORDER BY UserId", MAPPER); }

    public Optional<User> findById(Integer id) {
        return jdbc.query("SELECT * FROM dbo.Users WHERE UserId = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.query("SELECT * FROM dbo.Users WHERE Email = ?", MAPPER, email).stream().findFirst();
    }

    public Optional<User> findByRfid(String uidHash) {
        return jdbc.query("SELECT * FROM dbo.Users WHERE RfidTag = ?", MAPPER, uidHash).stream().findFirst();
    }

    public int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Users", Integer.class); }

    public int insert(User u) {
        return insertReturningId("INSERT INTO dbo.Users (Name, Email, PasswordHash, Role, RfidTag, IsActive) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", "UserId",
                u.name(), u.email(), u.passwordHash(), u.role(), u.rfidTag(), u.isActive());
    }

    public void update(Integer id, String name, String email, String rfidTag) {
        jdbc.update("UPDATE dbo.Users SET Name = ?, Email = ?, RfidTag = ? WHERE UserId = ?", name, email, rfidTag, id);
    }

    public void updateRole(Integer id, String role) { jdbc.update("UPDATE dbo.Users SET Role = ? WHERE UserId = ?", role, id); }

    public void toggleStatus(Integer id, boolean active) { callProc("{call dbo.sp_ToggleUserStatus(?, ?)}", id, active); }


    public void delete(Integer id) { jdbc.update("DELETE FROM dbo.Users WHERE UserId = ?", id); }

    public int countActiveByRole(String role) {
        return jdbc.queryForObject("SELECT dbo.fn_CountActiveUsersByRole(?)", Integer.class, role);
    }

    public void anonymize(Integer id) {
        jdbc.update("UPDATE dbo.Users SET Name = ?, Email = ?, PasswordHash = '!', RfidTag = NULL, IsActive = 0 "
                + "WHERE UserId = ?", "Anonymized-" + id, "anonymized-" + id + "@anonymized.invalid", id);
    }
}
