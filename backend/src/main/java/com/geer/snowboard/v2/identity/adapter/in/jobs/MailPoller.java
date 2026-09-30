package com.geer.snowboard.v2.identity.adapter.in.jobs;

import com.geer.snowboard.v2.identity.application.port.in.VerificationMailOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "identity.mail.worker.enabled", havingValue = "true", matchIfMissing = true)
public class MailPoller {
    private final VerificationMailOperations worker;
    public MailPoller(VerificationMailOperations worker) { this.worker = worker; }
    @Scheduled(fixedDelayString = "${identity.mail.worker.delay-ms:10000}")
    public void poll() { worker.runOnce(); }
    @Scheduled(cron = "0 10 * * * *")
    public void cleanup() { worker.cleanup(); }
}
