package com.project.skillforge.auth;

import com.project.skillforge.users.User;

/** Delivers a password reset link to a user. */
public interface ResetLinkSender {
    void send(User user, String link);
}