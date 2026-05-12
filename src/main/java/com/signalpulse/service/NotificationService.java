package com.signalpulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ConfigService configService;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    public void sendTopArticles(String recipientEmail, String content, String date) {
        String user = configService.getConfig("GMAIL_USER").orElse("");
        String pass = configService.getDecryptedConfig("GMAIL_PASSWORD");
        if (user.isBlank() || pass.isBlank()) {
            log.warn("Email skipped: GMAIL_USER or GMAIL_PASSWORD is not configured.");
            return;
        }

        String[] recipients = Arrays.stream(recipientEmail.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (recipients.length == 0) {
            log.warn("Email skipped: NOTIFY_RECIPIENTS contained no usable addresses.");
            return;
        }

        try {
            JavaMailSenderImpl sender = buildSender(user, pass);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(user);
            message.setTo(recipients);
            message.setSubject("SignalPulse: Top Articles Digest " + date);
            message.setText(content);
            sender.send(message);
            log.info("Email sent to {} recipient(s)", recipients.length);
        } catch (Exception e) {
            log.error("Failed to send email to {}", recipientEmail, e);
        }
    }

    /**
     * Build a sender per call so we never mutate a shared bean's credentials
     * (which would race with concurrent scans) and so we pick up admin UI
     * changes to GMAIL_USER / GMAIL_PASSWORD without an app restart.
     */
    private JavaMailSenderImpl buildSender(String user, String pass) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailHost);
        sender.setPort(mailPort);
        sender.setUsername(user);
        sender.setPassword(pass);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
