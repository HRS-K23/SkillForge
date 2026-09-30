package com.project.skillforge.auth;

import com.project.skillforge.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "skillforge.mail.enabled", havingValue = "true")
class SmtpResetLinkSender implements ResetLinkSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpResetLinkSender.class);

    private final JavaMailSender mail;
    private final String from;

    SmtpResetLinkSender(JavaMailSender mail, @Value("${skillforge.mail.from}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void send(User user, String link) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(user.getEmail());
            message.setSubject("Reset your SkillForge password");
            message.setText("Hi " + user.getName() + ",\n\nUse the link below to choose a new password. "
                    + "It expires soon and can only be used once.\n\n" + link
                    + "\n\nIf you did not request this, you can ignore this email.");
            mail.send(message);
        } catch (RuntimeException e) {
            log.error("Could not send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}