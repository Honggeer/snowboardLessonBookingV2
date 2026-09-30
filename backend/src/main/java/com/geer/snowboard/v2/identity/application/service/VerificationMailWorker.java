package com.geer.snowboard.v2.identity.application.service;

import com.geer.snowboard.v2.identity.application.port.in.VerificationMailOperations;
import com.geer.snowboard.v2.identity.application.port.out.VerificationMailQueue;
import com.geer.snowboard.v2.identity.application.port.out.VerificationMailSender;
import com.geer.snowboard.v2.identity.application.port.out.VerificationTokenCodec;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VerificationMailWorker implements VerificationMailOperations {
    private final VerificationMailQueue queue;
    private final VerificationMailSender sender;
    private final VerificationTokenCodec tokens;
    private final Clock clock;
    private final String publicUrl;

    public VerificationMailWorker(VerificationMailQueue queue, VerificationMailSender sender,
                                  VerificationTokenCodec tokens, Clock clock,
                                  @Value("${identity.public-url}") String publicUrl) {
        this.queue = queue;
        this.sender = sender;
        this.tokens = tokens;
        this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Override
    public void runOnce() {
        for (int index = 0; index < 10; index++) {
            VerificationMailQueue.Task task = queue.claim(clock.instant());
            if (task == null) break;
            if (!queue.isValid(task.id(), clock.instant())) {
                queue.skipped(task.id());
                continue;
            }
            try {
                String link = publicUrl + "/#verify?token=" + tokens.issue(task.verificationId());
                sender.send(task.email(), link);
                queue.sent(task.id());
            } catch (Exception exception) {
                long delay = Math.min(3600, 60L << Math.min(Math.max(task.attempts() - 1, 0), 6));
                queue.failed(task.id(), clock.instant().plusSeconds(delay), exception.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void cleanup() { queue.cleanup(clock.instant()); }
}
