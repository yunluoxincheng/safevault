package org.ttt.safevaultbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;


import java.util.Properties;

/**
 * 邮件配置
 * 配置阿里云邮件推送 SMTP 服务（使用 STARTTLS 端口 25）
 */
@Configuration
public class MailConfig {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Value("${spring.mail.protocol:smtp}")
    private String protocol;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean auth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private boolean starttlsEnable;

    @Value("${spring.mail.properties.mail.smtp.starttls.required:true}")
    private boolean starttlsRequired;

    @Value("${spring.mail.properties.mail.smtp.connectiontimeout:10000}")
    private int connectionTimeout;

    @Value("${spring.mail.properties.mail.smtp.timeout:10000}")
    private int timeout;

    @Value("${spring.mail.properties.mail.smtp.writetimeout:10000}")
    private int writeTimeout;

    @Value("${spring.mail.properties.mail.debug:false}")
    private boolean mailDebug;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        // 基础配置
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        String normalizedProtocol = protocol == null ? "smtp" : protocol.trim().toLowerCase();
        if (!"smtps".equals(normalizedProtocol)) {
            normalizedProtocol = "smtp";
        }
        mailSender.setProtocol(normalizedProtocol);

        // SMTP 属性配置
        Properties props = mailSender.getJavaMailProperties();

        props.put("mail.transport.protocol", normalizedProtocol);

        if ("smtps".equals(normalizedProtocol)) {
            // 465 隐式 TLS：使用 mail.smtps.*，并移除 mail.smtp.* 避免属性冲突
            props.remove("mail.smtp.auth");
            props.remove("mail.smtp.starttls.enable");
            props.remove("mail.smtp.starttls.required");
            props.remove("mail.smtp.ssl.checkserveridentity");
            props.remove("mail.smtp.ssl.trust");
            props.remove("mail.smtp.connectiontimeout");
            props.remove("mail.smtp.timeout");
            props.remove("mail.smtp.writetimeout");

            props.put("mail.smtps.auth", String.valueOf(auth));
            props.put("mail.smtps.ssl.enable", "true");
            props.put("mail.smtps.ssl.checkserveridentity", "true");
            props.put("mail.smtps.connectiontimeout", String.valueOf(connectionTimeout));
            props.put("mail.smtps.timeout", String.valueOf(timeout));
            props.put("mail.smtps.writetimeout", String.valueOf(writeTimeout));
        } else {
            // 普通 SMTP/STARTTLS：使用 mail.smtp.*
            props.remove("mail.smtps.auth");
            props.remove("mail.smtps.ssl.enable");
            props.remove("mail.smtps.ssl.checkserveridentity");
            props.remove("mail.smtps.connectiontimeout");
            props.remove("mail.smtps.timeout");
            props.remove("mail.smtps.writetimeout");

            props.put("mail.smtp.auth", String.valueOf(auth));
            props.put("mail.smtp.starttls.enable", String.valueOf(starttlsEnable));
            props.put("mail.smtp.starttls.required", String.valueOf(starttlsRequired));
            props.put("mail.smtp.ssl.checkserveridentity", "false");
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.connectiontimeout", String.valueOf(connectionTimeout));
            props.put("mail.smtp.timeout", String.valueOf(timeout));
            props.put("mail.smtp.writetimeout", String.valueOf(writeTimeout));
        }

        // 调试模式
        props.put("mail.debug", String.valueOf(mailDebug));

        // 编码配置
        props.put("mail.mime.charset", "UTF-8");
        props.put("mail.mime.encodeutf8", "true");

        return mailSender;
    }
}
