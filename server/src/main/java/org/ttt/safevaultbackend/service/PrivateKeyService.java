package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.dto.request.UploadPrivateKeyRequest;
import org.ttt.safevaultbackend.dto.response.PrivateKeyResponse;
import org.ttt.safevaultbackend.dto.response.UploadPrivateKeyResponse;
import org.ttt.safevaultbackend.entity.UserPrivateKey;
import org.ttt.safevaultbackend.exception.BusinessException;
import org.ttt.safevaultbackend.repository.UserPrivateKeyRepository;
import org.ttt.safevaultbackend.repository.UserRepository;

/**
 * 私钥存储服务
 * 处理用户加密私钥的云端存储和版本控制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateKeyService {

    private final UserPrivateKeyRepository privateKeyRepository;
    private final UserRepository userRepository;
    private final PrivateKeyResponseMapper privateKeyResponseMapper;

    /**
     * 上传加密私钥
     *
     * @param userId  用户 ID
     * @param request 上传请求
     * @return 上传响应
     */
    @Transactional
    public UploadPrivateKeyResponse uploadPrivateKey(String userId, UploadPrivateKeyRequest request) {
        // 验证用户存在
        if (!userRepository.existsById(userId)) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在");
        }

        var existingKeyOpt = privateKeyRepository.findById(userId);

        if (existingKeyOpt.isPresent()) {
            UserPrivateKey existingKey = existingKeyOpt.get();
            // 版本冲突检测
            if (isVersionOlder(request.getVersion(), existingKey.getVersion())) {
                log.warn("私钥版本冲突: userId={}, clientVersion={}, serverVersion={}",
                        userId, request.getVersion(), existingKey.getVersion());
                throw new BusinessException("VERSION_CONFLICT",
                        "服务器私钥版本较新: " + existingKey.getVersion());
            }

            // 更新现有私钥
            existingKey.setEncryptedPrivateKey(request.getEncryptedPrivateKey());
            existingKey.setIv(request.getIv());
            existingKey.setSalt(request.getSalt());
            existingKey.setAuthTag(request.getAuthTag());
            existingKey.setVersion(request.getVersion());
            privateKeyRepository.save(existingKey);
            log.info("更新私钥: userId={}, version={}", userId, request.getVersion());

            return privateKeyResponseMapper.mapUploadResponse(existingKey, existingKey.getUpdatedAt());
        } else {
            // 创建新私钥记录
            UserPrivateKey newKey = UserPrivateKey.builder()
                    .userId(userId)
                    .encryptedPrivateKey(request.getEncryptedPrivateKey())
                    .iv(request.getIv())
                    .salt(request.getSalt())
                    .authTag(request.getAuthTag())
                    .version(request.getVersion())
                    .build();
            privateKeyRepository.save(newKey);
            log.info("创建私钥: userId={}, version={}", userId, request.getVersion());

            return privateKeyResponseMapper.mapUploadResponse(newKey, newKey.getCreatedAt());
        }
    }

    /**
     * 获取加密私钥
     *
     * @param userId 用户 ID
     * @return 私钥响应
     */
    @Transactional(readOnly = true)
    public PrivateKeyResponse getPrivateKey(String userId) {
        UserPrivateKey key = privateKeyRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("PRIVATE_KEY_NOT_FOUND", "未找到私钥，请先上传"));

        return privateKeyResponseMapper.mapPrivateKeyResponse(key);
    }

    /**
     * 删除加密私钥
     *
     * @param userId 用户 ID
     */
    @Transactional
    public void deletePrivateKey(String userId) {
        if (!privateKeyRepository.existsById(userId)) {
            throw new BusinessException("PRIVATE_KEY_NOT_FOUND", "未找到私钥");
        }
        privateKeyRepository.deleteById(userId);
        log.info("删除私钥: userId={}", userId);
    }

    /**
     * 版本号比较
     * 简单版本比较: v1 < v2 < v3 ...
     *
     * @param clientVersion  客户端版本号
     * @param serverVersion  服务器版本号
     * @return 如果客户端版本较旧则返回 true
     */
    private boolean isVersionOlder(String clientVersion, String serverVersion) {
        if (clientVersion == null || serverVersion == null) {
            throw new BusinessException("INVALID_VERSION", "版本号不能为空");
        }
        try {
            String cv = clientVersion.startsWith("v") ? clientVersion.substring(1) : clientVersion;
            String sv = serverVersion.startsWith("v") ? serverVersion.substring(1) : serverVersion;
            int c = Integer.parseInt(cv);
            int s = Integer.parseInt(sv);
            return c < s;
        } catch (NumberFormatException e) {
            throw new BusinessException("INVALID_VERSION",
                    "版本号格式无效: clientVersion=" + clientVersion + ", serverVersion=" + serverVersion);
        }
    }
}
