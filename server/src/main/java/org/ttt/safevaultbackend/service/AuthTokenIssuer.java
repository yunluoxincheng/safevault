package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ttt.safevaultbackend.security.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class AuthTokenIssuer {

    private final JwtTokenProvider tokenProvider;

    public IssuedTokens issueTokens(String userId) {
        return new IssuedTokens(
                tokenProvider.generateAccessToken(userId),
                tokenProvider.generateRefreshToken(userId),
                tokenProvider.getAccessTokenExpirationSeconds()
        );
    }

    public record IssuedTokens(String accessToken, String refreshToken, long expiresInSeconds) {
    }
}
