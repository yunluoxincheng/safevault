package org.ttt.safevaultbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.ttt.safevaultbackend.service.TokenRevokeService;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT 认证过滤器
 * 安全加固：添加Token撤销检查（2.4）
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final TokenRevokeService tokenRevokeService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                    TokenRevokeService tokenRevokeService) {
        this.tokenProvider = tokenProvider;
        this.tokenRevokeService = tokenRevokeService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestPath = request.getRequestURI();
        logger.debug("Processing request: " + requestPath);

        try {
            String jwt = getJwtFromRequest(request);

            if (jwt == null) {
                logger.debug("No JWT token found in request headers for: " + requestPath);
            } else {
                logger.debug("JWT token found, length: " + jwt.length() + ", validating...");

                if (tokenProvider.validateToken(jwt)) {
                    String userId = tokenProvider.getUserIdFromToken(jwt);
                    String deviceId = request.getHeader("X-Device-ID");

                    // 安全加固：检查Token是否已被撤销（2.4）
                    if (tokenRevokeService.isTokenRevoked(jwt, userId, deviceId)) {
                        logger.warn("Token已撤销: userId=" + userId + ", deviceId=" + deviceId + ", path=" + requestPath);
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"error\":\"Token已撤销，请重新登录\"}");
                        return;
                    }

                    logger.info("JWT validated successfully for user: " + userId + " on: " + requestPath);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, new ArrayList<>());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.debug("SecurityContext updated with user: " + userId);
                } else {
                    logger.warn("JWT token validation failed for: " + requestPath);
                    // Token 验证失败的详细原因
                    if (tokenProvider.isTokenExpired(jwt)) {
                        logger.warn("JWT token is expired");
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context for: " + requestPath, ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
