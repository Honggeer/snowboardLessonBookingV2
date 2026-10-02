package com.geer.snowboard.v2.bookings.adapter.out.mail;

import com.geer.snowboard.v2.bookings.application.port.out.BookingMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpBookingMailSender implements BookingMailSender {
    private final ObjectProvider<JavaMailSender> provider;
    private final String from;

    public SmtpBookingMailSender(ObjectProvider<JavaMailSender> provider, Environment environment) {
        this.provider = provider;
        this.from = environment.getProperty("identity.mail.from", "");
    }

    @Override
    public void send(String email, String subject, String body) {
        JavaMailSender mail = provider.getIfAvailable();
        if (mail == null || from.isBlank()) throw new IllegalStateException("Mail transport is not configured");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);
        mail.send(message);
    }
}
