package com.tracker.flight.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderService {
    private final JavaMailSender sender;
    private final String from;

    public EmailSenderService(JavaMailSender sender, @Value("${app.mail.from:no-reply@flight-tracker.test}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public void send(String email, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }
}
