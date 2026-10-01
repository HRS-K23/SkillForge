package com.project.skillforge.auth;

import com.project.skillforge.users.User;
import com.project.skillforge.users.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Turns a verified JWT into an authentication, re-checking the account so that deleted,
 * disabled or password-changed accounts lose access immediately and roles are always current.
 */
@Component
public class UserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public UserJwtConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user;
        try {
            user = users.findById(UUID.fromString(jwt.getSubject())).orElse(null);
        } catch (IllegalArgumentException e) {
            user = null;
        }
        if (user == null || user.getStatus() != User.Status.ACTIVE) {
            throw new InvalidBearerTokenException("Account is no longer active");
        }
        Object tv = jwt.getClaims().get("tv");
        if (!(tv instanceof Number n) || n.intValue() != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Session was revoked");
        }
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                jwt.getSubject());
    }
}
