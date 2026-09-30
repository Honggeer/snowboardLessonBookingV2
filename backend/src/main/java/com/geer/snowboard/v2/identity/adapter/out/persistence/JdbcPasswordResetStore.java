package com.geer.snowboard.v2.identity.adapter.out.persistence;

import com.geer.snowboard.v2.identity.application.port.out.PasswordResetStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPasswordResetStore implements PasswordResetStore {
    private final JdbcTemplate jdbc;
    public JdbcPasswordResetStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Reset latest(String accountId) {
        return jdbc.query("""
                SELECT id,account_id,code_digest,expires_at,wrong_attempts,consumed_at,invalidated_at,created_at
                FROM identity_password_reset WHERE account_id=? ORDER BY created_at DESC,id DESC LIMIT 1
                """, rs -> rs.next() ? new Reset(rs.getString(1), rs.getString(2), rs.getBytes(3),
                instant(rs, 4), rs.getInt(5), instant(rs, 6), instant(rs, 7), instant(rs, 8)) : null, accountId);
    }

    @Override public void create(String id, String accountId, byte[] digest, Instant expiresAt, Instant now) {
        jdbc.update("""
                UPDATE identity_password_reset SET invalidated_at=?
                WHERE account_id=? AND consumed_at IS NULL AND invalidated_at IS NULL
                """, now, accountId);
        jdbc.update("""
                INSERT INTO identity_password_reset (id,account_id,code_digest,expires_at,created_at)
                VALUES (?,?,?,?,?)
                """, id, accountId, digest, expiresAt, now);
        jdbc.update("INSERT INTO identity_password_reset_mail_task (reset_id,next_attempt_at) VALUES (?,?)", id, now);
    }

    @Override public void wrongAttempt(String id, Instant now) {
        jdbc.update("""
                UPDATE identity_password_reset SET wrong_attempts=wrong_attempts+1
                WHERE id=? AND expires_at>? AND consumed_at IS NULL AND invalidated_at IS NULL AND wrong_attempts<5
                """, id, now);
    }

    @Override public boolean consume(String id, Instant now) {
        return jdbc.update("""
                UPDATE identity_password_reset SET consumed_at=?
                WHERE id=? AND expires_at>? AND consumed_at IS NULL AND invalidated_at IS NULL AND wrong_attempts<5
                """, now, id, now) == 1;
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
