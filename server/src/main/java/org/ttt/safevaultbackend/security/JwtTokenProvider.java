package org.ttt.safevaultbackend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token 提供者
 * 安全加固第三阶段：使用 RS256 非对称加密算法
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.rsa.private-key}")
    private String rsaPrivateKey;

    @Value("${jwt.rsa.public-key}")
    private String rsaPublicKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    /**
     * 启动时加载 RSA 密钥对
     * 安全加固第三阶段：使用 RSA 非对称加密
     */
    @PostConstruct
    public void init() {
        try {
            if (rsaPrivateKey == null || rsaPrivateKey.isEmpty()) {
                String errorMsg = "JWT_RSA_PRIVATE_KEY 环境变量未设置！应用无法启动。";
                logger.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            if (rsaPublicKey == null || rsaPublicKey.isEmpty()) {
                String errorMsg = "JWT_RSA_PUBLIC_KEY 环境变量未设置！应用无法启动。";
                logger.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            // 检查是否使用了示例密钥
            if (rsaPrivateKey.contains("your-rsa") || rsaPublicKey.contains("your-rsa")) {
                logger.warn("警告：JWT_RSA_* 密钥似乎是示例值！生产环境请使用真实的 RSA 密钥对。");
            }

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            // 加载私钥（PKCS8 格式）
            byte[] privateKeyBytes = Base64.getDecoder().decode(rsaPrivateKey);
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            this.privateKey = keyFactory.generatePrivate(privateKeySpec);

            // 加载公钥（X509 格式）
            byte[] publicKeyBytes = Base64.getDecoder().decode(rsaPublicKey);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            this.publicKey = keyFactory.generatePublic(publicKeySpec);

            logger.info("RSA 密钥对加载成功（算法: RSA, 密钥长度: {} 位）",
                publicKey instanceof java.security.interfaces.RSAPublicKey
                    ? ((java.security.interfaces.RSAPublicKey) publicKey).getModulus().bitLength()
                    : "未知");

        } catch (Exception e) {
            String errorMsg = "加载 RSA 密钥对失败！请检查环境变量配置。错误: " + e.getMessage();
            logger.error(errorMsg, e);
            throw new IllegalStateException(errorMsg, e);
        }
    }

    /**
     * 生成访问令牌
     * 安全加固第三阶段：使用 RS256 签名
     */
    public String generateAccessToken(String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 生成刷新令牌（带 jti 和 family claim）
     */
    public String generateRefreshToken(String userId, String jti, String family) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(userId)
                .claim("jti", jti)
                .claim("family", family)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 生成新的 jti
     */
    public String generateJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 从 refresh token 提取 jti
     */
    public String getJtiFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.get("jti", String.class) : null;
    }

    /**
     * 从 refresh token 提取 family
     */
    public String getFamilyFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.get("family", String.class) : null;
    }

    /**
     * 检查 token 是否为 refresh 类型
     */
    public boolean isRefreshToken(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) return false;
        return "refresh".equals(claims.get("type", String.class));
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 从令牌获取用户ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    /**
     * 验证令牌
     * 安全加固第三阶段：使用 RSA 公钥验证 RS256 签名
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            logger.debug("JWT token validated successfully");
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token is expired: " + e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.warn("JWT token is unsupported: " + e.getMessage());
        } catch (MalformedJwtException e) {
            logger.warn("JWT token is malformed: " + e.getMessage());
        } catch (SecurityException e) {
            logger.warn("JWT token signature validation failed: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("JWT token is illegal: " + e.getMessage());
        }
        return false;
    }

    /**
     * 检查令牌是否过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * 获取访问令牌过期时间（秒）
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

    /**
     * 获取刷新令牌过期时间（秒）
     */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpiration / 1000;
    }
}
