package org.ttt.safevaultbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ttt.safevaultbackend.dto.PasswordData;
import org.ttt.safevaultbackend.dto.request.UploadEccPublicKeyRequest;
import org.ttt.safevaultbackend.dto.response.*;
import org.ttt.safevaultbackend.service.UserService;

import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户", description = "用户信息管理")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "获取当前用户", description = "获取当前登录用户的详细信息")
    public ResponseEntity<UserProfileResponse> getCurrentUser() {
        UserProfileResponse response = userService.getUserProfile();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "获取指定用户", description = "通过用户ID获取用户信息")
    public ResponseEntity<UserProfileResponse> getUserById(@PathVariable String userId) {
        UserProfileResponse response = userService.getUserById(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户密钥信息
     *
     * 用于版本协商，返回用户的所有公钥信息
     *
     * @param userId 用户 ID
     * @return 用户密钥信息
     */
    @GetMapping("/{userId}/keys")
    @Operation(summary = "获取用户密钥信息", description = "获取用户的所有公钥信息（RSA、X25519、Ed25519），用于版本协商")
    public ResponseEntity<UserKeyInfoResponse> getUserKeys(@PathVariable String userId) {
        UserKeyInfoResponse response = userService.getUserKeys(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索用户", description = "通过用户ID或用户名模糊搜索用户")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(@RequestParam String query) {
        List<UserSearchResponse> response = userService.searchUsers(query);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/qrcode")
    @Operation(summary = "生成二维码", description = "生成用于扫码分享的二维码数据")
    public ResponseEntity<QRCodeResponse> generateQRCode() {
        QRCodeResponse response = userService.generateQRCode();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me")
    @Operation(summary = "更新用户信息", description = "更新当前用户的显示名称")
    public ResponseEntity<UserProfileResponse> updateDisplayName(
            @RequestParam String displayName) {
        UserProfileResponse response = userService.updateDisplayName(displayName);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/statistics")
    @Operation(summary = "获取用户统计", description = "获取当前用户的分享统计信息")
    public ResponseEntity<PasswordData> getUserStatistics() {
        PasswordData response = userService.getUserStatistics();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/ecc-public-keys")
    @Operation(summary = "上传 ECC 公钥", description = "上传用户的 X25519/Ed25519 椭圆曲线公钥")
    public ResponseEntity<UploadEccPublicKeyResponse> uploadEccPublicKey(
            @Valid @RequestBody UploadEccPublicKeyRequest request) {
        UploadEccPublicKeyResponse response = userService.uploadEccPublicKey(request);
        return ResponseEntity.ok(response);
    }
}
