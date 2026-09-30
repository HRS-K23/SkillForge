package com.project.skillforge.auth;

import com.project.skillforge.users.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development fallback: writes the link to the log. Enable SMTP (skillforge.mail.enabled) for real delivery. */
@Component
@ConditionalOnProperty(name = "skillforge.mail.enabled", havingValue = "false", matchIfMissing = true)
class LoggingResetLinkSender implements ResetLinkSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingResetLinkSender.class);

    @Override
    public void send(User user, String link) {
        log.info("Password reset link for {} (mail is disabled): {}", user.getEmail(), link);
    }
}