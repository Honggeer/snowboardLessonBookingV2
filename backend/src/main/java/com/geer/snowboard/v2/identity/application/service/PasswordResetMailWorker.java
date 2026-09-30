package com.geer.snowboard.v2.identity.application.service;

import com.geer.snowboard.v2.identity.application.port.in.PasswordResetMailOperations;
import com.geer.snowboard.v2.identity.application.port.out.PasswordResetCodeCodec;
import com.geer.snowboard.v2.identity.application.port.out.PasswordResetMailQueue;
import com.geer.snowboard.v2.identity.application.port.out.PasswordResetMailSender;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetMailWorker implements PasswordResetMailOperations {
    private final PasswordResetMailQueue queue;
    private final PasswordResetMailSender sender;
    private final PasswordResetCodeCodec codes;
    private final Clock clock;

    public PasswordResetMailWorker(PasswordResetMailQueue queue, PasswordResetMailSender sender,
                                   PasswordResetCodeCodec codes, Clock clock) {
        this.queue = queue;
        this.sender = sender;
        this.codes = codes;
        this.clock = clock;
    }

    @Override public void runOnce() {
        for (int index = 0; index < 10; index++) {
            PasswordResetMailQueue.Task task = queue.claim(clock.instant());
            if (task == null) break;
            if (!queue.isValid(task.id(), clock.instant())) {
                queue.skipped(task.id());
                continue;
            }
            try {
                sender.send(task.email(), codes.issue(task.resetId()));
                queue.sent(task.id());
            } catch (Exception exception) {
                long delay = Math.min(3600, 60L << Math.min(Math.max(task.attempts() - 1, 0), 6));
                queue.failed(task.id(), clock.instant().plusSeconds(delay), exception.getClass().getSimpleName());
            }
        }
    }

    @Override public void cleanup() { queue.cleanup(clock.instant()); }
}
