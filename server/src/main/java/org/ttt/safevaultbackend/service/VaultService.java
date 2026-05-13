package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.dto.request.VaultInitRequest;
import org.ttt.safevaultbackend.dto.request.VaultSyncRequest;
import org.ttt.safevaultbackend.dto.response.VaultResponse;
import org.ttt.safevaultbackend.dto.response.VaultSyncResponse;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.entity.UserVault;
import org.ttt.safevaultbackend.exception.BusinessException;
import org.ttt.safevaultbackend.exception.ResourceNotFoundException;
import org.ttt.safevaultbackend.repository.UserRepository;
import org.ttt.safevaultbackend.repository.UserVaultRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 密码库服务
 * 零知识架构：服务器只存储加密数据，不解密
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VaultService {

    private final UserVaultRepository vaultRepository;
    private final UserRepository userRepository;
    private final VaultResponseMapper vaultResponseMapper;

    /**
     * 获取用户的密码库
     * @param userId 用户 ID
     * @return 加密的密码库数据
     */
    @Transactional(readOnly = true)
    public VaultResponse getVault(String userId) {
        UserVault vault = vaultRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserVault", "userId", userId));

        return vaultResponseMapper.mapToResponse(vault);
    }

    /**
     * 初始化新用户的密码库
     * @param userId 用户 ID
     * @param request 初始化请求
     * @return 创建的密码库
     */
    @Transactional
    public VaultResponse initializeVault(String userId, VaultInitRequest request) {
        // 检查用户是否存在
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        // 检查是否已有密码库
        if (vaultRepository.existsByUserId(userId)) {
            throw new BusinessException("VAULT_ALREADY_EXISTS", "用户已有密码库");
        }

        // 创建新密码库
        UserVault vault = UserVault.builder()
                .vaultId(UUID.randomUUID().toString())
                .userId(userId)
                .encryptedData(request.getEncryptedData())
                .dataIv(request.getDataIv())
                .dataAuthTag(request.getDataAuthTag())
                .salt(request.getSalt())
                .version(1L)
                .lastSyncedAt(LocalDateTime.now())
                .build();

        vault = vaultRepository.save(vault);
        log.info("初始化密码库: userId={}, vaultId={}", userId, vault.getVaultId());

        return vaultResponseMapper.mapToResponse(vault);
    }

    /**
     * 同步密码库（支持冲突检测）
     * @param userId 用户 ID
     * @param request 同步请求
     * @return 同步结果
     */
    @Transactional
    public VaultSyncResponse syncVault(String userId, VaultSyncRequest request) {
        // 检查用户是否存在
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));

        // 获取现有密码库（如果存在）
        var existingVaultOpt = vaultRepository.findByUserId(userId);

        if (existingVaultOpt.isEmpty()) {
            // 用户没有密码库，直接创建
            return createNewVault(userId, request);
        }

        UserVault existingVault = existingVaultOpt.get();

        // 检查版本冲突
        if (hasVersionConflict(existingVault.getVersion(), request.getClientVersion())) {
            return handleConflict(existingVault, request);
        }

        // 没有冲突，直接更新
        return updateVault(existingVault, request);
    }

    /**
     * 删除用户的密码库
     * @param userId 用户 ID
     */
    @Transactional
    public void deleteVault(String userId) {
        if (!vaultRepository.existsByUserId(userId)) {
            throw new ResourceNotFoundException("UserVault", "userId", userId);
        }

        vaultRepository.deleteByUserId(userId);
        log.info("删除密码库: userId={}", userId);
    }

    /**
     * 检查是否存在版本冲突
     */
    private boolean hasVersionConflict(Long serverVersion, Long clientVersion) {
        // 客户端版本号小于服务器版本号，说明有其他设备更新了密码库
        return clientVersion < serverVersion;
    }

    /**
     * 处理版本冲突
     */
    private VaultSyncResponse handleConflict(UserVault serverVault, VaultSyncRequest request) {
        if (request.isForceSync()) {
            // 强制同步：覆盖服务器数据
            return updateVault(serverVault, request);
        }

        // 返回冲突信息，包含服务器数据供客户端合并
        VaultResponse serverVaultResponse = vaultResponseMapper.mapToResponse(serverVault);

        return VaultSyncResponse.builder()
                .success(false)
                .hasConflict(true)
                .conflictMessage("密码库已被其他设备更新，请先同步最新数据")
                .serverVersion(serverVault.getVersion())
                .clientVersion(request.getClientVersion())
                .serverVault(serverVaultResponse)
                .build();
    }

    /**
     * 更新现有密码库
     */
    private VaultSyncResponse updateVault(UserVault vault, VaultSyncRequest request) {
        // 更新加密数据
        vault.setEncryptedData(request.getEncryptedData());
        vault.setDataIv(request.getDataIv());
        vault.setDataAuthTag(request.getDataAuthTag());
        vault.setSalt(request.getSalt());
        vault.setVersion(vault.getVersion() + 1);
        vault.setLastSyncedAt(LocalDateTime.now());

        vault = vaultRepository.save(vault);

        return VaultSyncResponse.builder()
                .success(true)
                .hasConflict(false)
                .serverVersion(vault.getVersion() - 1)
                .clientVersion(request.getClientVersion())
                .newVersion(vault.getVersion())
                .vault(vaultResponseMapper.mapToResponse(vault))
                .lastSyncedAt(vault.getLastSyncedAt())
                .build();
    }

    /**
     * 创建新密码库（同步请求时）
     */
    private VaultSyncResponse createNewVault(String userId, VaultSyncRequest request) {
        UserVault vault = UserVault.builder()
                .vaultId(UUID.randomUUID().toString())
                .userId(userId)
                .encryptedData(request.getEncryptedData())
                .dataIv(request.getDataIv())
                .dataAuthTag(request.getDataAuthTag())
                .salt(request.getSalt())
                .version(1L)
                .lastSyncedAt(LocalDateTime.now())
                .build();

        vault = vaultRepository.save(vault);

        return VaultSyncResponse.builder()
                .success(true)
                .hasConflict(false)
                .serverVersion(0L)
                .clientVersion(request.getClientVersion())
                .newVersion(1L)
                .vault(vaultResponseMapper.mapToResponse(vault))
                .lastSyncedAt(vault.getLastSyncedAt())
                .build();
    }

    /**
     * 将实体映射到响应对象
     */
}
