package org.ttt.safevaultbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ttt.safevaultbackend.annotation.RateLimit;
import org.ttt.safevaultbackend.dto.request.*;
import org.ttt.safevaultbackend.dto.response.AuthResponse;
import org.ttt.safevaultbackend.dto.response.CompleteRegistrationResponse;
import org.ttt.safevaultbackend.dto.response.DeviceListResponse;
import org.ttt.safevaultbackend.dto.response.EmailLoginResponse;
import org.ttt.safevaultbackend.dto.response.EmailRegistrationResponse;
import org.ttt.safevaultbackend.dto.response.LoginPrecheckResponse;
import org.ttt.safevaultbackend.dto.response.LogoutResponse;
import org.ttt.safevaultbackend.dto.response.RemoveDeviceResponse;
import org.ttt.safevaultbackend.dto.response.VerifyEmailResponse;
import org.ttt.safevaultbackend.dto.response.VerificationStatusResponse;
import org.ttt.safevaultbackend.dto.DeviceInfo;
import org.ttt.safevaultbackend.service.AuthService;

/**
 * 认证控制器
 * 支持旧的设备 ID 认证和新的邮箱认证
 * 安全加固：继承 BaseController，从 SecurityContext 获取用户 ID（2.5）
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "用户注册、登录和令牌刷新")
public class AuthController extends BaseController {

    private final AuthService authService;

    // ========== 旧版认证（保留兼容） ==========

    @PostMapping("/register")
    @Operation(summary = "用户注册（旧版）", description = "使用设备 ID 注册新用户")
    @Deprecated
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录（旧版）", description = "使用设备 ID 登录")
    @Deprecated
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/by-username")
    @Operation(summary = "通过用户名登录", description = "使用用户名登录（支持多账号切换）")
    public ResponseEntity<AuthResponse> loginByUsername(@Valid @RequestBody LoginByUsernameRequest request) {
        AuthResponse response = authService.loginByUsername(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用刷新令牌获取新的访问令牌")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String authorizationHeader) {
        String refreshToken = authorizationHeader.replace("Bearer ", "");
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    // ========== 统一邮箱认证 API ==========

    @PostMapping("/register-email")
    @Operation(summary = "邮箱注册（第一步）", description = "使用邮箱和用户名发起注册，发送验证邮件")
    @RateLimit(requests = 3, per = "minute")
    public ResponseEntity<EmailRegistrationResponse> registerWithEmail(
            @Valid @RequestBody EmailRegistrationRequest request) {
        EmailRegistrationResponse response = authService.registerWithEmail(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "验证邮箱", description = "使用验证令牌验证邮箱")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        VerifyEmailResponse response = authService.verifyEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "重新发送验证邮件", description = "重新发送邮箱验证邮件")
    @RateLimit(requests = 10, per = "hour")
    public ResponseEntity<EmailRegistrationResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        EmailRegistrationResponse response = authService.resendVerificationEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/complete-registration")
    @Operation(summary = "完成注册", description = "邮箱验证后设置主密码并完成注册")
    public ResponseEntity<CompleteRegistrationResponse> completeRegistration(
            @Valid @RequestBody CompleteRegistrationRequest request) {
        CompleteRegistrationResponse response = authService.completeRegistration(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verification-status")
    @Operation(summary = "检查邮箱验证状态", description = "查询邮箱是否已完成验证")
    public ResponseEntity<VerificationStatusResponse> checkVerificationStatus(
            @io.swagger.v3.oas.annotations.Parameter(description = "要查询的邮箱地址", required = true)
            @RequestParam String email) {
        VerificationStatusResponse response = authService.checkVerificationStatus(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login-by-email")
    @Operation(summary = "邮箱登录", description = "使用邮箱和派生密钥签名登录（Challenge-Response 机制）")
    @RateLimit(requests = 5, per = "minute")
    public ResponseEntity<EmailLoginResponse> loginByEmail(
            @Valid @RequestBody LoginByEmailRequest request) {
        EmailLoginResponse response = authService.loginByEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login-precheck")
    @Operation(summary = "登录预检查", description = "获取服务器挑战码（nonce），用于 Challenge-Response 登录")
    @RateLimit(requests = 10, per = "minute")
    public ResponseEntity<LoginPrecheckResponse> loginPrecheck(
            @Valid @RequestBody LoginPrecheckRequest request) {
        LoginPrecheckResponse response = authService.loginPrecheck(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "注销登录", description = "清除服务器端会话")
    public ResponseEntity<LogoutResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody LogoutRequest request) {
        String userId = getCurrentUserId();
        // 从 Authorization header 中提取令牌
        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.replace("Bearer ", "");
        }
        LogoutResponse response = authService.logout(userId, token, request);
        return ResponseEntity.ok(response);
    }

    // ========== 设备管理 API ==========

    @GetMapping("/devices")
    @Operation(summary = "获取设备列表", description = "获取当前用户的所有设备列表")
    public ResponseEntity<DeviceListResponse> getDevices() {
        String userId = getCurrentUserId();
        java.util.List<DeviceInfo> devices = authService.getUserDevices(userId);
        DeviceListResponse response = DeviceListResponse.builder()
                .devices(devices)
                .totalDevices(devices.size())
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/devices/{deviceId}")
    @Operation(summary = "移除设备", description = "移除指定的设备")
    public ResponseEntity<RemoveDeviceResponse> removeDevice(
            @io.swagger.v3.oas.annotations.Parameter(description = "要移除的设备 ID")
            @PathVariable String deviceId) {
        String userId = getCurrentUserId();
        boolean removed = authService.removeDevice(userId, deviceId);
        RemoveDeviceResponse response = RemoveDeviceResponse.builder()
                .success(removed)
                .message(removed ? "设备已成功移除" : "设备不存在或移除失败")
                .removedDeviceId(deviceId)
                .build();
        return ResponseEntity.ok(response);
    }

    // ========== 调试 API ==========
    // 安全加固：调试端点仅在开发环境可用，生产环境不可访问

    @Profile("dev")
    @GetMapping("/debug/pending-user")
    @Operation(summary = "调试：获取待验证用户状态", description = "根据邮箱或token查询Redis中的待验证用户状态（仅开发环境）")
    public ResponseEntity<java.util.Map<String, Object>> debugPendingUser(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String token) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("timestamp", java.time.LocalDateTime.now());

        if (email != null && !email.isEmpty()) {
            result.put("query", "email: " + email);
            result.put("pendingUser", authService.debugGetPendingUserByEmail(email));
        } else if (token != null && !token.isEmpty()) {
            result.put("query", "token: " + token);
            result.put("pendingUser", authService.debugGetPendingUserByToken(token));
        } else {
            result.put("error", "请提供 email 或 token 参数");
        }

        return ResponseEntity.ok(result);
    }

    @Profile("dev")
    @GetMapping("/debug/redis-raw")
    @Operation(summary = "调试：获取 Redis 原始数据", description = "查看 Redis 中存储的原始值和类型（仅开发环境）")
    public ResponseEntity<java.util.Map<String, Object>> debugRedisRaw(
            @RequestParam String email) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("timestamp", java.time.LocalDateTime.now());
        result.put("email", email);
        result.put("rawData", authService.debugGetRedisRawValue(email));
        return ResponseEntity.ok(result);
    }
}
