package com.geer.snowboard.v2.identity.adapter.out.persistence;

import com.geer.snowboard.v2.identity.application.port.out.IdentityStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIdentityStore implements IdentityStore {
    private static final RowMapper<Account> ACCOUNT = (rs, row) -> new Account(
            rs.getString("id"), rs.getString("name"), rs.getString("level"), rs.getString("email"),
            rs.getString("email_key"), rs.getString("role"), rs.getString("password_hash"),
            instant(rs, "verified_at"), rs.getLong("credential_version"));
    private final JdbcTemplate jdbc;

    public JdbcIdentityStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean insertAccount(Account account, Instant now) {
        try {
            jdbc.update("INSERT INTO identity_account (id,email,email_key,name,level,role,password_hash,created_at) VALUES (?,?,?,?,?,?,?,?)",
                    account.id(), account.email(), account.emailKey(), account.name(), account.level(),
                    account.role(), account.passwordHash(), now);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override public Account findByEmail(String key) {
        return jdbc.query("SELECT * FROM identity_account WHERE email_key=?", ACCOUNT, key).stream().findFirst().orElse(null);
    }
    @Override public Account findById(String id) {
        return jdbc.query("SELECT * FROM identity_account WHERE id=?", ACCOUNT, id).stream().findFirst().orElse(null);
    }
    @Override public Account lockById(String id) {
        return jdbc.query("SELECT * FROM identity_account WHERE id=? FOR UPDATE", ACCOUNT, id).stream().findFirst().orElse(null);
    }
    @Override public Long credentialVersion(String id) {
        return jdbc.query("SELECT credential_version FROM identity_account WHERE id=? AND verified_at IS NOT NULL",
                rs -> rs.next() ? rs.getLong(1) : null, id);
    }
    @Override public void changePassword(String id, String passwordHash) {
        if (jdbc.update("UPDATE identity_account SET password_hash=?,credential_version=credential_version+1 WHERE id=?",
                passwordHash, id) != 1) throw new IllegalStateException("Account disappeared while changing password");
    }
    @Override public String studentPhone(String id) {
        return jdbc.query("SELECT contact_phone FROM identity_account WHERE id=? AND role='STUDENT'",
                rs -> rs.next() ? rs.getString(1) : null, id);
    }
    @Override public void saveStudentPhone(String id, String phone) {
        if (jdbc.update("UPDATE identity_account SET contact_phone=? WHERE id=? AND role='STUDENT'", phone, id) != 1)
            throw new IllegalStateException("Account disappeared while saving contact phone");
    }
    @Override public Map<String, String> studentPhones(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<String, String> phones = new HashMap<>();
        jdbc.query("SELECT id,contact_phone FROM identity_account WHERE role='STUDENT' AND contact_phone IS NOT NULL AND id IN ("
                + placeholders + ")", (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                phones.put(rs.getString("id"), rs.getString("contact_phone")), ids.toArray());
        return Map.copyOf(phones);
    }
    @Override public boolean coachExists() {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM identity_account WHERE role='COACH')", Boolean.class));
    }
    @Override public void replaceVerification(String id, String accountId, byte[] digest, Instant expires, Instant now) {
        jdbc.update("UPDATE identity_verification SET invalidated_at=? WHERE account_id=? AND consumed_at IS NULL AND invalidated_at IS NULL", now, accountId);
        jdbc.update("INSERT INTO identity_verification (id,account_id,token_digest,expires_at,created_at) VALUES (?,?,?,?,?)",
                id, accountId, digest, expires, now);
        jdbc.update("INSERT INTO identity_mail_task (verification_id,next_attempt_at) VALUES (?,?)", id, now);
    }
    @Override public String accountIdForVerification(String id) {
        return jdbc.query("SELECT account_id FROM identity_verification WHERE id=?", rs -> rs.next() ? rs.getString(1) : null, id);
    }
    @Override public boolean consumeVerification(String id, byte[] digest, Instant now) {
        int updated = jdbc.update("UPDATE identity_verification SET consumed_at=? WHERE id=? AND token_digest=? AND expires_at>? AND consumed_at IS NULL AND invalidated_at IS NULL",
                now, id, digest, now);
        if (updated == 0) return false;
        String accountId = accountIdForVerification(id);
        return jdbc.update("UPDATE identity_account SET verified_at=? WHERE id=? AND verified_at IS NULL", now, accountId) == 1;
    }
    @Override public boolean mayResend(String accountId, Instant now) {
        Instant last = jdbc.query("SELECT MAX(created_at) FROM identity_verification WHERE account_id=?",
                rs -> rs.next() ? instant(rs, 1) : null, accountId);
        return last == null || !last.plusSeconds(60).isAfter(now);
    }
    @Override public boolean allowAttempt(String key, int max, Instant now, long seconds) {
        Instant expiry = now.plusSeconds(seconds);
        jdbc.update("INSERT INTO identity_rate_limit (rate_key,attempts,expires_at) VALUES (?,1,?) ON DUPLICATE KEY UPDATE attempts=IF(expires_at<=?,1,attempts+1), expires_at=IF(expires_at<=?,VALUES(expires_at),expires_at)",
                key, expiry, now, now);
        return attempts(key, now) <= max;
    }
    @Override public int attempts(String key, Instant now) {
        Integer result = jdbc.query("SELECT attempts FROM identity_rate_limit WHERE rate_key=? AND expires_at>?",
                rs -> rs.next() ? rs.getInt(1) : 0, key, now);
        return result == null ? 0 : result;
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
    private static Instant instant(ResultSet rs, int column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
