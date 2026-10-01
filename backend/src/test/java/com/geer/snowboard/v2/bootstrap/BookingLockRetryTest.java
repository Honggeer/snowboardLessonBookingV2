package com.geer.snowboard.v2.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

class BookingLockRetryTest {
    private final BookingLockRetry retry = new BookingLockRetry();

    @Test void retriesRecognizedLockErrorsTwice() {
        var attempts = new AtomicInteger();
        String result = retry.run(() -> {
            if (attempts.incrementAndGet() < 3) throw lockError(1213);
            return "created";
        });
        assertThat(result).isEqualTo("created");
        assertThat(attempts).hasValue(3);
    }

    @Test void returnsServiceErrorAfterRetryLimit() {
        var attempts = new AtomicInteger();
        assertThatThrownBy(() -> retry.run(() -> {
            attempts.incrementAndGet();
            throw lockError(1205);
        })).isInstanceOf(BusinessProblem.class)
                .satisfies(error -> assertThat(((BusinessProblem) error).status()).isEqualTo(503));
        assertThat(attempts).hasValue(3);
    }

    @Test void doesNotRetryOtherDatabaseErrors() {
        var attempts = new AtomicInteger();
        var error = lockError(1062);
        assertThatThrownBy(() -> retry.run(() -> {
            attempts.incrementAndGet();
            throw error;
        })).isSameAs(error);
        assertThat(attempts).hasValue(1);
    }

    private static CannotAcquireLockException lockError(int code) {
        return new CannotAcquireLockException("mysql", new SQLException("simulated", "40001", code));
    }
}
