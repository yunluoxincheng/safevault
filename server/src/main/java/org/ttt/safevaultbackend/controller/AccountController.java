package org.ttt.safevaultbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ttt.safevaultbackend.dto.response.DeleteAccountResponse;
import org.ttt.safevaultbackend.service.AccountService;
import org.ttt.safevaultbackend.security.JwtTokenProvider;

/**
 * 账户控制器
 * 处理账户级操作，如删除账户
 */
@RestController
@RequestMapping("/v1/account")
@RequiredArgsConstructor
@Tag(name = "账户管理", description = "账户级操作，如删除账户")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;
    private final JwtTokenProvider tokenProvider;

    /**
     * 删除账户
     * 删除当前登录用户及其所有相关数据
     *
     * @param authorizationHeader JWT Token (Authorization: Bearer <token>)
     * @return 删除结果
     */
    @DeleteMapping
    @Operation(summary = "删除账户", description = "永久删除当前用户账户及所有相关数据（密码库、分享记录等）")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeleteAccountResponse> deleteAccount(
            @RequestHeader("Authorization") String authorizationHeader) {

        try {
            // 从 JWT Token 中提取用户ID
            String token = authorizationHeader.replace("Bearer ", "");
            String userId = tokenProvider.getUserIdFromToken(token);

            // 验证 Token 有效性
            if (!tokenProvider.validateToken(token)) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(DeleteAccountResponse.builder()
                                .success(false)
                                .message("Token 无效或已过期")
                                .build());
            }

            // 执行账户删除
            accountService.deleteAccount(userId);

            return ResponseEntity
                    .ok(DeleteAccountResponse.builder()
                            .success(true)
                            .message("账户已删除")
                            .build());

        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DeleteAccountResponse.builder()
                            .success(false)
                            .message("账户删除失败: " + e.getMessage())
                            .build());
        }
    }
}
