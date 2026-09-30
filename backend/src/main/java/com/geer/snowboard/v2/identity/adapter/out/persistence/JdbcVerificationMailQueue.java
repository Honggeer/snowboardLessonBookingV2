package com.geer.snowboard.v2.identity.adapter.out.persistence;

import com.geer.snowboard.v2.identity.application.port.out.VerificationMailQueue;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcVerificationMailQueue implements VerificationMailQueue {
    private final JdbcTemplate jdbc;
    public JdbcVerificationMailQueue(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public Task claim(Instant now) {
        Long id = jdbc.query("""
                SELECT id FROM identity_mail_task
                WHERE (status='PENDING' AND next_attempt_at<=?)
                   OR (status='CLAIMED' AND claim_until<=?)
                ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, rs -> rs.next() ? rs.getLong(1) : null, now, now);
        if (id == null) return null;
        jdbc.update("UPDATE identity_mail_task SET status='CLAIMED',attempts=attempts+1,claim_until=? WHERE id=?",
                now.plusSeconds(30), id);
        return jdbc.query("""
                SELECT t.id,v.id AS verification_id,a.email,t.attempts FROM identity_mail_task t
                JOIN identity_verification v ON v.id=t.verification_id
                JOIN identity_account a ON a.id=v.account_id WHERE t.id=?
                """, rs -> rs.next() ? new Task(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)) : null, id);
    }

    @Override public boolean isValid(long taskId, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM identity_mail_task t
                JOIN identity_verification v ON v.id=t.verification_id
                JOIN identity_account a ON a.id=v.account_id
                WHERE t.id=? AND t.status='CLAIMED' AND v.expires_at>?
                AND v.consumed_at IS NULL AND v.invalidated_at IS NULL AND a.verified_at IS NULL)
                """, Boolean.class, taskId, now));
    }
    @Override public void sent(long id) {
        jdbc.update("UPDATE identity_mail_task SET status='SENT',claim_until=NULL,last_error=NULL WHERE id=? AND status='CLAIMED'", id);
    }
    @Override public void failed(long id, Instant nextAttempt, String reason) {
        jdbc.update("UPDATE identity_mail_task SET status='PENDING',claim_until=NULL,next_attempt_at=?,last_error=? WHERE id=? AND status='CLAIMED'",
                nextAttempt, reason, id);
    }
    @Override public void skipped(long id) {
        jdbc.update("UPDATE identity_mail_task SET status='SKIPPED',claim_until=NULL WHERE id=? AND status='CLAIMED'", id);
    }
    @Override public void cleanup(Instant now) {
        jdbc.update("DELETE FROM identity_rate_limit WHERE expires_at<?", now.minusSeconds(3600));
        jdbc.update("DELETE FROM identity_mail_task WHERE status IN ('SENT','SKIPPED') AND next_attempt_at<?", now.minusSeconds(7 * 86400));
    }
}
