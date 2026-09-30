package com.geer.snowboard.v2.identity.adapter.out.mail;

import com.geer.snowboard.v2.identity.application.port.out.VerificationMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpVerificationMailSender implements VerificationMailSender {
    private final ObjectProvider<JavaMailSender> provider;
    private final String from;
    public SmtpVerificationMailSender(ObjectProvider<JavaMailSender> provider,
                                      @Value("${identity.mail.from:}") String from) {
        this.provider = provider;
        this.from = from;
    }
    @Override public void send(String email, String link) {
        JavaMailSender mail = provider.getIfAvailable();
        if (mail == null || from.isBlank()) throw new IllegalStateException("Mail transport is not configured");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("验证 GEER 账号邮箱");
        message.setText("请在 24 小时内打开以下链接验证你的 GEER 账号：\n\n" + link
                + "\n\n如果不是你本人申请，可以忽略这封邮件。");
        mail.send(message);
    }
}
