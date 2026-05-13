package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.dto.*;
import org.ttt.safevaultbackend.dto.request.CreateContactShareRequest;
import org.ttt.safevaultbackend.dto.response.*;
import org.ttt.safevaultbackend.entity.*;
import org.ttt.safevaultbackend.exception.BusinessException;
import org.ttt.safevaultbackend.exception.ResourceNotFoundException;
import org.ttt.safevaultbackend.repository.ContactShareRepository;
import org.ttt.safevaultbackend.repository.FriendshipRepository;
import org.ttt.safevaultbackend.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 联系人分享服务
 * 仅支持好友间的密码分享
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactShareService {

    private final ContactShareRepository contactShareRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final WebSocketService webSocketService;
    private final ContactSharePayloadMapper payloadMapper;

    /**
     * 创建联系人分享
     */
    @Transactional
    public ContactShareResponse createContactShare(CreateContactShareRequest request, String fromUserId) {
        // 验证发送方用户存在
        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", fromUserId));

        // 验证接收方用户存在
        User toUser = userRepository.findById(request.getToUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", request.getToUserId()));

        // 不能分享给自己
        if (fromUserId.equals(request.getToUserId())) {
            throw new BusinessException("CANNOT_SHARE_TO_SELF", "不能分享给自己");
        }

        // 验证好友关系
        Friendship friendship = validateFriendship(fromUserId, request.getToUserId());

        // 检查是否已有活跃的分享
        Optional<ContactShare> existingShare = contactShareRepository.findExistingShare(
                fromUserId,
                request.getToUserId(),
                request.getPasswordId(),
                Arrays.asList(ContactShareStatus.PENDING, ContactShareStatus.ACCEPTED)
        );

        if (existingShare.isPresent()) {
            throw new BusinessException("SHARE_ALREADY_EXISTS", "已存在对此密码的活跃分享");
        }

        // 验证加密版本
        String encryptionVersion = request.getEncryptionVersion();
        if (encryptionVersion == null || (!"v1".equals(encryptionVersion) && !"v2".equals(encryptionVersion))) {
            throw new BusinessException("INVALID_ENCRYPTION_VERSION", "加密版本必须是 v1 或 v2");
        }

        // 创建分享
        String shareId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(request.getExpiresInMinutes());

        // 构建加密数据
        Map<String, String> encryptedData = payloadMapper.buildEncryptedData(request);

        ContactShare contactShare = ContactShare.builder()
                .shareId(shareId)
                .fromUser(fromUser)
                .toUser(toUser)
                .passwordId(request.getPasswordId())
                .encryptedData(payloadMapper.serializeEncryptedData(encryptedData))
                .encryptionVersion(encryptionVersion)
                .canView(request.getPermission().isCanView())
                .canSave(request.getPermission().isCanSave())
                .isRevocable(request.getPermission().isRevocable())
                .status(ContactShareStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        contactShare = contactShareRepository.save(contactShare);

        // 发送实时通知
        sendShareNotification(toUser.getUserId(), fromUser, shareId, "NEW_SHARE");

        log.info("Created contact share: {} from {} to {} (encryption: {})", shareId, fromUserId, request.getToUserId(), encryptionVersion);

        return ContactShareResponse.builder()
                .shareId(shareId)
                .passwordId(request.getPasswordId())
                .status(contactShare.getStatus())
                .createdAt(contactShare.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                .expiresAt(expiresAt.toEpochSecond(ZoneOffset.UTC))
                .encryptionVersion(encryptionVersion)
                .build();
    }

    /**
     * 接收分享详情
     */
    @Transactional(readOnly = true)
    public ReceivedContactShareResponse receiveShare(String shareId, String userId) {
        ContactShare share = contactShareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("ContactShare", "shareId", shareId));

        // 验证接收权限
        if (!share.getToUser().getUserId().equals(userId)) {
            throw new BusinessException("ACCESS_DENIED", "无权访问此分享");
        }

        // 检查状态
        if (share.getStatus() == ContactShareStatus.EXPIRED) {
            throw new BusinessException("SHARE_EXPIRED", "分享已过期");
        }
        if (share.getStatus() == ContactShareStatus.REVOKED) {
            throw new BusinessException("SHARE_REVOKED", "分享已被撤销");
        }

        // 解析加密数据
        return payloadMapper.mapToReceivedShareResponse(share);
    }

    /**
     * 接受分享
     */
    @Transactional
    public AcceptShareResponse acceptShare(String shareId, String userId) {
        ContactShare share = contactShareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("ContactShare", "shareId", shareId));

        // 验证接收权限
        if (!share.getToUser().getUserId().equals(userId)) {
            throw new BusinessException("ACCESS_DENIED", "无权接受此分享");
        }

        // 检查状态
        if (share.getStatus() == ContactShareStatus.EXPIRED) {
            throw new BusinessException("SHARE_EXPIRED", "分享已过期");
        }
        if (share.getStatus() == ContactShareStatus.REVOKED) {
            throw new BusinessException("SHARE_REVOKED", "分享已被撤销");
        }
        if (share.getStatus() == ContactShareStatus.ACCEPTED) {
            throw new BusinessException("SHARE_ALREADY_ACCEPTED", "分享已被接受");
        }

        // 检查是否允许保存
        if (!share.isCanSave()) {
            throw new BusinessException("SAVE_NOT_ALLOWED", "此分享不允许保存");
        }

        // 更新状态
        share.setStatus(ContactShareStatus.ACCEPTED);
        share.setAcceptedAt(LocalDateTime.now());
        contactShareRepository.save(share);

        // 通知发送方
        sendShareNotification(share.getFromUser().getUserId(), share.getToUser(), shareId, "SHARE_ACCEPTED");

        log.info("Accepted contact share: {} by {}", shareId, userId);

        return AcceptShareResponse.builder()
                .shareId(shareId)
                .status(ContactShareStatus.ACCEPTED)
                .acceptedAt(share.getAcceptedAt().toEpochSecond(ZoneOffset.UTC))
                .build();
    }

    /**
     * 撤销分享
     */
    @Transactional
    public void revokeShare(String shareId, String userId) {
        ContactShare share = contactShareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("ContactShare", "shareId", shareId));

        // 验证发送方权限
        if (!share.getFromUser().getUserId().equals(userId)) {
            throw new BusinessException("ACCESS_DENIED", "无权撤销此分享");
        }

        // 检查是否可撤销
        if (!share.isRevocable()) {
            throw new BusinessException("SHARE_NOT_REVOCABLE", "此分享不可撤销");
        }

        // 更新状态
        if (share.getStatus() != ContactShareStatus.REVOKED) {
            share.setStatus(ContactShareStatus.REVOKED);
            share.setRevokedAt(LocalDateTime.now());
            contactShareRepository.save(share);

            // 通知接收方
            sendShareNotification(share.getToUser().getUserId(), share.getFromUser(), shareId, "SHARE_REVOKED");

            log.info("Revoked contact share: {} by {}", shareId, userId);
        }
    }

    /**
     * 获取发送的分享列表
     */
    @Transactional(readOnly = true)
    public List<SentContactShareResponse> getSentShares(String userId) {
        List<ContactShare> shares = contactShareRepository.findActiveSharesByFromUser(
                userId,
                Arrays.asList(ContactShareStatus.PENDING, ContactShareStatus.ACCEPTED)
        );

        return shares.stream()
                .map(payloadMapper::mapToSentShareResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取接收的分享列表
     */
    @Transactional(readOnly = true)
    public List<ReceivedContactShareResponse> getReceivedShares(String userId) {
        List<ContactShare> shares = contactShareRepository.findActiveSharesByToUser(
                userId,
                Arrays.asList(ContactShareStatus.PENDING, ContactShareStatus.ACCEPTED)
        );

        return shares.stream()
                .map(payloadMapper::mapToReceivedShareResponse)
                .collect(Collectors.toList());
    }

    /**
     * 定时任务：检查并更新过期分享
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void updateExpiredShares() {
        List<ContactShare> expiredShares = contactShareRepository.findExpiredShares(
                LocalDateTime.now(),
                ContactShareStatus.EXPIRED
        );

        for (ContactShare share : expiredShares) {
            share.setStatus(ContactShareStatus.EXPIRED);
            contactShareRepository.save(share);

            // 通知相关用户
            sendShareNotification(share.getToUser().getUserId(), share.getFromUser(), share.getShareId(), "SHARE_EXPIRED");
        }

        if (!expiredShares.isEmpty()) {
            log.info("Updated {} expired shares", expiredShares.size());
        }
    }

    /**
     * 验证好友关系
     */
    private Friendship validateFriendship(String userA, String userB) {
        // 检查正向好友关系
        Friendship friendship = friendshipRepository.findByUserIdAAndUserIdB(userA, userB)
                .orElse(null);

        if (friendship == null) {
            // 检查反向好友关系
            friendship = friendshipRepository.findByUserIdAAndUserIdB(userB, userA)
                    .orElse(null);
        }

        if (friendship == null) {
            throw new BusinessException("NOT_FRIENDS", "只能分享给好友");
        }

        if (friendship.getStatus() != FriendStatus.ACCEPTED) {
            throw new BusinessException("NOT_FRIENDS", "只能分享给已接受的好友");
        }

        return friendship;
    }

    /**
     * 构建加密数据
     */
    /**
     * 序列化加密数据
     */
    /**
     * 反序列化加密数据
     */
    /**
     * 发送分享通知
     */
    private void sendShareNotification(String toUserId, User fromUser, String shareId, String type) {
        ShareNotificationMessage notification = ShareNotificationMessage.builder()
                .type(type)
                .shareId(shareId)
                .fromUserId(fromUser.getUserId())
                .fromDisplayName(fromUser.getDisplayName())
                .message(buildNotificationMessage(type, fromUser.getDisplayName()))
                .timestamp(System.currentTimeMillis())
                .build();

        webSocketService.sendShareNotification(toUserId, notification);
    }

    /**
     * 构建通知消息
     */
    private String buildNotificationMessage(String type, String displayName) {
        return switch (type) {
            case "NEW_SHARE" -> String.format("%s 向你分享了一个密码", displayName);
            case "SHARE_ACCEPTED" -> String.format("%s 接受了你的分享", displayName);
            case "SHARE_REVOKED" -> "分享已被撤销";
            case "SHARE_EXPIRED" -> "分享已过期";
            default -> "密码分享通知";
        };
    }

    /**
     * 映射到发送分享响应
     */
    /**
     * 映射到接收分享响应
     */
}
