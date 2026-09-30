package com.geer.snowboard.v2.identity.adapter.out.mail;

import com.geer.snowboard.v2.identity.application.port.out.PasswordResetMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {
    private final ObjectProvider<JavaMailSender> provider;
    private final String from;

    public SmtpPasswordResetMailSender(ObjectProvider<JavaMailSender> provider,
                                       @Value("${identity.mail.from:}") String from) {
        this.provider = provider;
        this.from = from;
    }

    @Override public void send(String email, String code) {
        JavaMailSender mail = provider.getIfAvailable();
        if (mail == null || from.isBlank()) throw new IllegalStateException("Mail transport is not configured");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("GEER 密码找回验证码");
        message.setText("你的 GEER 密码找回验证码是：" + code
                + "\n\n验证码 10 分钟内有效。如申请过多次，请使用最近一次的验证码。"
                + "\n\n如果不是你本人申请，可以忽略这封邮件。");
        mail.send(message);
    }
}
