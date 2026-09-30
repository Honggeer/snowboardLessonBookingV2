package com.geer.snowboard.v2.identity.application.service;

import com.geer.snowboard.v2.identity.application.port.in.PasswordRecoveryOperations;
import com.geer.snowboard.v2.identity.application.port.in.RateLimited;
import com.geer.snowboard.v2.identity.application.port.out.IdentityStore;
import com.geer.snowboard.v2.identity.application.port.out.PasswordHashes;
import com.geer.snowboard.v2.identity.application.port.out.PasswordResetCodeCodec;
import com.geer.snowboard.v2.identity.application.port.out.PasswordResetStore;
import com.geer.snowboard.v2.identity.domain.RecoveryRules;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService implements PasswordRecoveryOperations {
    private final IdentityStore accounts;
    private final PasswordResetStore resets;
    private final PasswordResetCodeCodec codes;
    private final PasswordHashes passwords;
    private final Clock clock;

    public PasswordRecoveryService(IdentityStore accounts, PasswordResetStore resets,
                                   PasswordResetCodeCodec codes, PasswordHashes passwords, Clock clock) {
        this.accounts = accounts;
        this.resets = resets;
        this.codes = codes;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = RateLimited.class)
    public void request(String email, String sourceIp) {
        String key = RecoveryRules.emailKey(email);
        Instant now = clock.instant();
        boolean ipAllowed = accounts.allowAttempt("reset:request:ip:" + sourceIp, 10, now, 3600);
        boolean emailAllowed = accounts.allowAttempt("reset:request:email:" + key, 3, now, 3600);
        if (!ipAllowed || !emailAllowed) throw new RateLimited();
        IdentityStore.Account found = accounts.findByEmail(key);
        if (found == null) return;
        IdentityStore.Account account = accounts.lockById(found.id());
        if (account == null || account.verifiedAt() == null) return;
        PasswordResetStore.Reset latest = resets.latest(account.id());
        if (latest != null && latest.createdAt().plusSeconds(60).isAfter(now)) return;
        String id = UUID.randomUUID().toString();
        String code = codes.issue(id);
        resets.create(id, account.id(), codes.digest(id, code), now.plusSeconds(600), now);
    }

    @Override
    @Transactional(noRollbackFor = RateLimited.class)
    public Grant verify(String email, String code, String sourceIp) {
        String key = RecoveryRules.emailKey(email);
        RecoveryRules.code(code);
        Instant now = clock.instant();
        if (!accounts.allowAttempt("reset:verify:ip:" + sourceIp, 30, now, 900)) throw new RateLimited();
        IdentityStore.Account found = accounts.findByEmail(key);
        if (found == null) return null;
        IdentityStore.Account account = accounts.lockById(found.id());
        if (account == null || account.verifiedAt() == null) return null;
        PasswordResetStore.Reset reset = resets.latest(account.id());
        if (!usable(reset, now)) return null;
        if (!MessageDigest.isEqual(reset.codeDigest(), codes.digest(reset.id(), code))) {
            resets.wrongAttempt(reset.id(), now);
            return null;
        }
        Instant grantExpiry = now.plusSeconds(300);
        if (grantExpiry.isAfter(reset.expiresAt())) grantExpiry = reset.expiresAt();
        return new Grant(account.id(), reset.id(), grantExpiry);
    }

    @Override
    @Transactional
    public boolean complete(Grant grant, String newPassword, String confirmPassword) {
        Instant now = clock.instant();
        if (grant == null || !grant.expiresAt().isAfter(now)) return false;
        String validated = RecoveryRules.password(newPassword, confirmPassword);
        IdentityStore.Account account = accounts.lockById(grant.accountId());
        if (account == null || account.verifiedAt() == null) return false;
        PasswordResetStore.Reset reset = resets.latest(account.id());
        if (!usable(reset, now) || !reset.id().equals(grant.resetId())) return false;
        if (passwords.matches(validated, account.passwordHash())) {
            throw new IllegalArgumentException("New password must differ from current password");
        }
        String encoded = passwords.encode(validated);
        if (!resets.consume(reset.id(), now)) return false;
        accounts.changePassword(account.id(), encoded);
        return true;
    }

    private static boolean usable(PasswordResetStore.Reset reset, Instant now) {
        return reset != null && reset.expiresAt().isAfter(now) && reset.wrongAttempts() < 5
                && reset.consumedAt() == null && reset.invalidatedAt() == null;
    }
}
