package com.signalpulse.service;

import com.signalpulse.entity.AppConfig;
import com.signalpulse.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final org.springframework.mail.javamail.JavaMailSender mailSender;
    private final ConfigService configService;

    public void sendTopArticles(String recipientEmail, String content, String date) {
        try {
            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl) {
                org.springframework.mail.javamail.JavaMailSenderImpl impl = (org.springframework.mail.javamail.JavaMailSenderImpl) mailSender;
                impl.setUsername(configService.getConfig("GMAIL_USER").orElse(impl.getUsername()));
                impl.setPassword(configService.getDecryptedConfig("GMAIL_PASSWORD"));
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(configService.getConfig("GMAIL_USER").orElse("signalpulse@gmail.com"));
            
            String[] recipients = recipientEmail.split(",");
            log.info("Recipient email: {}", recipients);
            log.info("Recipient email[0]: {}", recipients[0]);
            message.setTo(recipients);
            
            message.setSubject("SignalPulse: Top Articles Digest".concat(date));
            message.setText(content);
            mailSender.send(message);
            log.info("Email sent to: {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}", recipientEmail, e);
        }
    }
}
