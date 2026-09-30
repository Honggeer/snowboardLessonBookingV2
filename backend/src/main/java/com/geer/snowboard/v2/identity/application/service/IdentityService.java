package com.geer.snowboard.v2.identity.application.service;

import com.geer.snowboard.v2.identity.application.port.in.AccountView;
import com.geer.snowboard.v2.identity.application.port.in.IdentityOperations;
import com.geer.snowboard.v2.identity.application.port.in.RegisterCommand;
import com.geer.snowboard.v2.identity.application.port.in.RateLimited;
import com.geer.snowboard.v2.identity.application.port.out.IdentityStore;
import com.geer.snowboard.v2.identity.application.port.out.PasswordHashes;
import com.geer.snowboard.v2.identity.application.port.out.VerificationTokenCodec;
import com.geer.snowboard.v2.identity.domain.Registration;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService implements IdentityOperations {
    private final IdentityStore store;
    private final PasswordHashes passwords;
    private final VerificationTokenCodec tokens;
    private final Clock clock;
    private final String dummyHash;

    public IdentityService(IdentityStore store, PasswordHashes passwords, VerificationTokenCodec tokens, Clock clock) {
        this.store = store;
        this.passwords = passwords;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public void register(RegisterCommand command, String sourceIp) {
        if (!store.allowAttempt("register:ip:" + sourceIp, 10, clock.instant(), 3600)) throw new RateLimited();
        Registration input = Registration.create(command.name(), command.level(), command.email(), command.password());
        String accountId = UUID.randomUUID().toString();
        IdentityStore.Account account = new IdentityStore.Account(accountId, input.name(), input.level().name(),
                input.email(), input.emailKey(), "STUDENT", passwords.encode(input.password()), null);
        if (store.insertAccount(account, clock.instant())) newVerification(accountId);
    }

    @Override
    @Transactional
    public boolean verify(String token) {
        String id = tokens.idOf(token);
        if (id == null) return false;
        String accountId = store.accountIdForVerification(id);
        if (accountId == null || store.lockById(accountId) == null) return false;
        return store.consumeVerification(id, tokens.digest(token), clock.instant());
    }

    @Override
    @Transactional
    public void resend(String email, String sourceIp) {
        Instant now = clock.instant();
        if (!store.allowAttempt("resend:ip:" + sourceIp, 10, now, 3600)
                || !store.allowAttempt("resend:email:" + canonical(email), 3, now, 3600)) throw new RateLimited();
        IdentityStore.Account account = store.findByEmail(canonical(email));
        if (account != null) {
            account = store.lockById(account.id());
            if (account != null && account.verifiedAt() == null && store.mayResend(account.id(), now)) {
                newVerification(account.id());
            }
        }
    }

    @Override
    @Transactional
    public AccountView authenticate(String email, String password, String sourceIp) {
        Instant now = clock.instant();
        String emailKey = canonical(email);
        String emailRateKey = "login:email:" + emailKey;
        String ipRateKey = "login:ip:" + sourceIp;
        if (store.attempts(emailRateKey, now) >= 5 || store.attempts(ipRateKey, now) >= 30) throw new RateLimited();
        IdentityStore.Account account = store.findByEmail(emailKey);
        String hash = account != null && account.verifiedAt() != null ? account.passwordHash() : dummyHash;
        boolean validPassword = password != null && passwords.matches(password, hash);
        if (account == null || account.verifiedAt() == null || !validPassword) {
            store.allowAttempt(emailRateKey, 5, now, 900);
            store.allowAttempt(ipRateKey, 30, now, 900);
            return null;
        }
        return view(account);
    }

    @Override
    public AccountView findById(String id) {
        IdentityStore.Account account = store.findById(id);
        return account == null || account.verifiedAt() == null ? null : view(account);
    }

    @Override
    @Transactional
    public void createCoach(String name, String email, String password) {
        Registration input = Registration.create(name, "零基础", email, password);
        if (store.coachExists()) throw new IllegalStateException("Coach already exists");
        String accountId = UUID.randomUUID().toString();
        IdentityStore.Account coach = new IdentityStore.Account(accountId, input.name(), null,
                input.email(), input.emailKey(), "COACH", passwords.encode(input.password()), null);
        if (!store.insertAccount(coach, clock.instant())) throw new IllegalStateException("Email already in use");
        newVerification(accountId);
    }

    private void newVerification(String accountId) {
        String id = UUID.randomUUID().toString();
        String token = tokens.issue(id);
        Instant now = clock.instant();
        store.replaceVerification(id, accountId, tokens.digest(token), now.plusSeconds(86400), now);
    }

    private static String canonical(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static AccountView view(IdentityStore.Account account) {
        String level = account.level() == null ? null
                : com.geer.snowboard.v2.identity.domain.Level.valueOf(account.level()).label();
        return new AccountView(account.id(), account.role(), account.name(), level);
    }

}
