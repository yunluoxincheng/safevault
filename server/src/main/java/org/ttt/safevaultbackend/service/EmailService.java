package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.io.IOException;
import java.io.InputStream;

/**
 * 邮件服务
 * 使用阿里云 SMTP 服务直接发送验证邮件
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.from:noreply@safevault.com}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8080/api}")
    private String baseUrl;

    /**
     * 发送邮箱验证邮件（HTML 格式）
     *
     * @param toEmail         收件人邮箱
     * @param verificationUrl 验证链接（safevault://verify-email?token=xxx）
     * @return 是否发送成功
     */
    public boolean sendVerificationEmail(String toEmail, String verificationUrl) {
        try {
            log.info("Preparing to send verification email to: {}", toEmail);
            log.info("From email: {}", fromEmail);
            log.info("Verification URL: {}", verificationUrl);

            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("验证您的 SafeVault 邮箱");

            String htmlContent = buildVerificationEmailHtml(toEmail, verificationUrl);
            String plainText = buildVerificationEmailText(toEmail, verificationUrl);
            helper.setText(plainText, htmlContent);

            log.info("Sending email with MIME message...");

            javaMailSender.send(mimeMessage);

            log.info("Verification email sent successfully to: {}", toEmail);
            return true;

        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送纯文本验证邮件（备用方案）
     *
     * @param toEmail         收件人邮箱
     * @param verificationUrl 验证链接
     * @return 是否发送成功
     */
    public boolean sendVerificationEmailText(String toEmail, String verificationUrl) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("验证您的 SafeVault 邮箱");

            String textContent = buildVerificationEmailText(toEmail, verificationUrl);
            message.setText(textContent);

            javaMailSender.send(message);

            log.info("Text verification email sent to: {}", toEmail);
            return true;

        } catch (Exception e) {
            log.error("Failed to send text verification email to {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送密码重置邮件
     *
     * @param toEmail  收件人邮箱
     * @param resetUrl 重置链接
     * @return 是否发送成功
     */
    public boolean sendPasswordResetEmail(String toEmail, String resetUrl) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("重置您的 SafeVault 密码");

            String htmlContent = buildPasswordResetEmailHtml(toEmail, resetUrl);
            String plainText = buildPasswordResetEmailText(toEmail, resetUrl);
            helper.setText(plainText, htmlContent);

            javaMailSender.send(mimeMessage);

            log.info("Password reset email sent to: {}", toEmail);
            return true;

        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 构建验证邮件 HTML 内容
     */
    private String buildVerificationEmailHtml(String email, String verificationUrl) {
        String token = extractTokenFromUrl(verificationUrl);
        String httpsUrl = buildEmailVerificationHttpUrl(token);

        try {
            String template = loadTemplate("email/verification-email.html");
            return template
                    .replace("{{baseUrl}}", baseUrl)
                    .replace("{{verificationUrl}}", httpsUrl)
                    .replace("{{timestamp}}", LocalDateTime.now().toString());
        } catch (IOException e) {
            log.error("Failed to load verification email template", e);
            return buildFallbackEmail(httpsUrl);
        }
    }

    /**
     * 从URL中提取token
     */
    private String extractTokenFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        // 从 safevault://verify-email?token=xxx 或 https://xxx/verify/email?token=xxx 中提取token
        int tokenIndex = url.indexOf("token=");
        if (tokenIndex >= 0) {
            String token = url.substring(tokenIndex + 6);
            // 移除可能的额外参数
            int ampersandIndex = token.indexOf('&');
            if (ampersandIndex >= 0) {
                token = token.substring(0, ampersandIndex);
            }
            return token;
        }
        return "";
    }

    /**
     * 使用可配置 baseUrl 构建邮件中的验证链接
     */
    private String buildEmailVerificationHttpUrl(String token) {
        String normalizedBaseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "http://localhost:8080/api"
                : baseUrl.replaceAll("/+$", "");
        return normalizedBaseUrl + "/verify/email?token=" + token;
    }

    /**
     * 构建验证邮件纯文本内容
     */
    private String buildVerificationEmailText(String email, String verificationUrl) {
        try {
            String template = loadTemplate("email/verification-email.txt");
            return template
                    .replace("{{verificationUrl}}", verificationUrl)
                    .replace("{{timestamp}}", LocalDateTime.now().toString());
        } catch (IOException e) {
            log.error("Failed to load verification email text template", e);
            return "SafeVault - 验证您的邮箱\n\n请访问以下链接验证邮箱: " + verificationUrl;
        }
    }

    /**
     * 构建密码重置邮件 HTML 内容
     */
    private String buildPasswordResetEmailHtml(String email, String resetUrl) {
        try {
            String template = loadTemplate("email/password-reset.html");
            return template
                    .replace("{{baseUrl}}", baseUrl)
                    .replace("{{resetUrl}}", resetUrl)
                    .replace("{{timestamp}}", LocalDateTime.now().toString());
        } catch (IOException e) {
            log.error("Failed to load password reset email template", e);
            return buildFallbackEmail(resetUrl);
        }
    }

    private String buildPasswordResetEmailText(String email, String resetUrl) {
        try {
            String template = loadTemplate("email/password-reset.txt");
            return template
                    .replace("{{resetUrl}}", resetUrl)
                    .replace("{{timestamp}}", LocalDateTime.now().toString());
        } catch (IOException e) {
            log.error("Failed to load password reset email text template", e);
            return "SafeVault - 重置您的密码\n\n请访问以下链接重置密码: " + resetUrl;
        }
    }

    /**
     * 从 resources/templates 目录加载模板文件
     */
    private String loadTemplate(String templatePath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("templates/" + templatePath)) {
            if (inputStream == null) {
                throw new IOException("Template not found: " + templatePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 构建备用邮件内容（当模板加载失败时使用）
     */
    private String buildFallbackEmail(String url) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2>SafeVault</h2>
                    <p>请点击以下链接完成操作：</p>
                    <p><a href="%s">%s</a></p>
                </div>
            </body>
            </html>
            """, url, url);
    }
}
