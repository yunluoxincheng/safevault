package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.dto.PasswordData;
import org.ttt.safevaultbackend.dto.request.UploadEccPublicKeyRequest;
import org.ttt.safevaultbackend.dto.response.*;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.exception.ResourceNotFoundException;
import org.ttt.safevaultbackend.repository.ContactShareRepository;
import org.ttt.safevaultbackend.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ContactShareRepository contactShareRepository;

    /**
     * 获取当前用户 ID
     */
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String) {
            return (String) authentication.getPrincipal();
        }
        throw new ResourceNotFoundException("User", "current", "N/A");
    }

    /**
     * 获取当前用户配置
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile() {
        String userId = getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        return buildUserProfileResponse(user);
    }

    /**
     * 通过 ID 获取用户
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserById(String targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", targetUserId));

        return buildUserProfileResponse(user);
    }

    /**
     * 获取用户密钥信息
     *
     * 用于版本协商，返回用户的所有公钥信息
     *
     * @param userId 用户 ID
     * @return 用户密钥信息
     */
    @Transactional(readOnly = true)
    public UserKeyInfoResponse getUserKeys(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        return UserKeyInfoResponse.builder()
                .userId(user.getUserId())
                .rsaPublicKey(user.getPublicKey())
                .x25519PublicKey(user.getX25519PublicKey())
                .ed25519PublicKey(user.getEd25519PublicKey())
                .keyVersion(user.getKeyVersion())
                .build();
    }

    /**
     * 搜索用户
     */
    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(String query) {
        List<User> users = userRepository.searchByUserIdOrUsername(query);

        return users.stream()
                .map(this::buildUserSearchResponse)
                .collect(Collectors.toList());
    }

    /**
     * 更新显示名称
     */
    @Transactional
    public UserProfileResponse updateDisplayName(String displayName) {
        String userId = getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        user.setDisplayName(displayName);
        user = userRepository.save(user);

        return buildUserProfileResponse(user);
    }

    /**
     * 生成二维码数据（用于扫码分享）
     */
    @Transactional(readOnly = true)
    public QRCodeResponse generateQRCode() {
        String userId = getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        // 生成临时 Token
        String tempToken = UUID.randomUUID().toString();

        // 二维码数据格式：safevault:receive:userId:tempToken
        String qrCodeData = String.format("safevault:receive:%s:%s", user.getUserId(), tempToken);

        // 有效期：30 分钟
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

        return QRCodeResponse.builder()
                .qrCodeData(qrCodeData)
                .expiresAt(expiresAt.toEpochSecond(ZoneOffset.UTC))
                .size(300)
                .build();
    }

    /**
     * 获取用户统计
     */
    @Transactional(readOnly = true)
    public PasswordData getUserStatistics() {
        String userId = getCurrentUserId();

        long createdSharesCount = contactShareRepository.findByFromUser_UserIdOrderByCreatedAtDesc(userId).size();
        long receivedSharesCount = contactShareRepository.findByToUser_UserIdOrderByCreatedAtDesc(userId).size();

        return PasswordData.builder()
                .title("用户统计")
                .username(String.format("创建分享：%d", createdSharesCount))
                .encryptedPassword(String.format("接收分享：%d", receivedSharesCount))
                .build();
    }

    /**
     * 上传 X25519/Ed25519 椭圆曲线公钥
     *
     * @param request 上传公钥请求
     * @return 上传响应
     */
    @Transactional
    public UploadEccPublicKeyResponse uploadEccPublicKey(UploadEccPublicKeyRequest request) {
        String userId = getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        // 更新用户的 ECC 公钥
        user.setX25519PublicKey(request.getX25519PublicKey());
        user.setEd25519PublicKey(request.getEd25519PublicKey());
        user.setKeyVersion(request.getKeyVersion());
        user.setPublicKeysUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);

        return UploadEccPublicKeyResponse.builder()
                .success(true)
                .message("ECC 公钥上传成功")
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 构建用户响应（包含 ECC 公钥）
     */
    private UserProfileResponse buildUserProfileResponse(User user) {
        int shareCount = contactShareRepository.findByFromUser_UserIdOrderByCreatedAtDesc(user.getUserId()).size();

        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .publicKey(user.getPublicKey())
                .x25519PublicKey(user.getX25519PublicKey())
                .ed25519PublicKey(user.getEd25519PublicKey())
                .keyVersion(user.getKeyVersion())
                .createdAt(user.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                .shareCount(shareCount)
                .build();
    }

    /**
     * 构建用户搜索响应（包含 ECC 公钥）
     */
    private UserSearchResponse buildUserSearchResponse(User user) {
        return UserSearchResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .publicKey(user.getPublicKey())
                .x25519PublicKey(user.getX25519PublicKey())
                .ed25519PublicKey(user.getEd25519PublicKey())
                .keyVersion(user.getKeyVersion())
                .build();
    }
}
