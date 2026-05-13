package org.ttt.safevaultbackend.service;

import org.springframework.stereotype.Component;

@Component
public class EmailVerificationLinkFactory {

    private static final String REGISTRATION_DEEP_LINK = "safevault://verify-email?token=";

    public String buildRegistrationVerificationUrl(String token) {
        return REGISTRATION_DEEP_LINK + token;
    }
}
