package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.dto.FriendNotificationMessage;
import org.ttt.safevaultbackend.dto.request.RespondFriendRequestRequest;
import org.ttt.safevaultbackend.dto.request.SendFriendRequestRequest;
import org.ttt.safevaultbackend.dto.response.FriendDto;
import org.ttt.safevaultbackend.dto.response.FriendRequestDto;
import org.ttt.safevaultbackend.dto.response.UserSearchResult;
import org.ttt.safevaultbackend.entity.FriendRequest;
import org.ttt.safevaultbackend.entity.Friendship;
import org.ttt.safevaultbackend.entity.FriendStatus;
import org.ttt.safevaultbackend.entity.FriendRequestStatus;
import org.ttt.safevaultbackend.entity.User;
import org.ttt.safevaultbackend.exception.BusinessException;
import org.ttt.safevaultbackend.exception.ResourceNotFoundException;
import org.ttt.safevaultbackend.repository.FriendRequestRepository;
import org.ttt.safevaultbackend.repository.FriendshipRepository;
import org.ttt.safevaultbackend.repository.UserRepository;
import org.ttt.safevaultbackend.websocket.WebSocketConnectionManager;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 好友系统服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final WebSocketService webSocketService;
    private final WebSocketConnectionManager connectionManager;

    @Override
    @Transactional
    public String sendFriendRequest(SendFriendRequestRequest request, String fromUserId) {
        // 验证用户存在
        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", fromUserId));
        User toUser = userRepository.findById(request.getToUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", request.getToUserId()));

        // 不能发送好友请求给自己
        if (fromUserId.equals(request.getToUserId())) {
            throw new BusinessException("CANNOT_ADD_SELF", "不能添加自己为好友");
        }

        // 检查是否已经是好友
        if (friendshipRepository.existsByUserIdAAndUserIdB(fromUserId, request.getToUserId())) {
            throw new BusinessException("ALREADY_FRIENDS", "用户已经是好友");
        }

        // 检查是否已有待处理的请求
        if (friendRequestRepository.existsByFromUserIdAndToUserIdAndStatus(
                fromUserId, request.getToUserId(), FriendRequestStatus.PENDING)) {
            throw new BusinessException("PENDING_REQUEST_EXISTS", "已存在待处理的好友请求");
        }

        // 创建好友请求
        FriendRequest friendRequest = FriendRequest.builder()
                .id(UUID.randomUUID().toString())
                .fromUserId(fromUserId)
                .toUserId(request.getToUserId())
                .message(request.getMessage())
                .status(FriendRequestStatus.PENDING)
                .build();

        friendRequest = friendRequestRepository.save(friendRequest);
        log.info("Friend request created: fromUserId={}, toUserId={}, requestId={}",
                fromUserId, request.getToUserId(), friendRequest.getId());

        // 发送WebSocket通知
        FriendNotificationMessage notification = FriendNotificationMessage.builder()
                .type("FRIEND_REQUEST")
                .requestId(friendRequest.getId())
                .fromUserId(fromUserId)
                .fromDisplayName(fromUser.getDisplayName())
                .toUserId(request.getToUserId())
                .message(request.getMessage() != null
                    ? request.getMessage()
                    : String.format("%s 请求添加你为好友", fromUser.getDisplayName()))
                .timestamp(System.currentTimeMillis())
                .build();

        webSocketService.sendFriendNotification(request.getToUserId(), notification);

        return friendRequest.getId();
    }

    @Override
    @Transactional
    public void respondToFriendRequest(String requestId, RespondFriendRequestRequest request, String toUserId) {
        FriendRequest friendRequest = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("FriendRequest", "requestId", requestId));

        // 验证权限：只有接收方可以响应请求
        if (!friendRequest.getToUserId().equals(toUserId)) {
            throw new BusinessException("UNAUTHORIZED", "无权响应此好友请求");
        }

        // 检查请求状态
        if (friendRequest.getStatus() != FriendRequestStatus.PENDING) {
            throw new BusinessException("REQUEST_ALREADY_PROCESSED", "请求已被处理");
        }

        if (request.getAccept()) {
            // 接受好友请求：创建双向好友关系
            Friendship friendship = Friendship.builder()
                    .id(UUID.randomUUID().toString())
                    .userIdA(friendRequest.getFromUserId())
                    .userIdB(friendRequest.getToUserId())
                    .status(FriendStatus.ACCEPTED)
                    .createdBy(toUserId)
                    .build();

            friendshipRepository.save(friendship);
            log.info("Friendship created: userIdA={}, userIdB={}",
                    friendRequest.getFromUserId(), friendRequest.getToUserId());

            // 发送接受通知给发送方
            User fromUser = userRepository.findById(friendRequest.getFromUserId()).orElse(null);
            User toUser = userRepository.findById(toUserId).orElse(null);

            if (fromUser != null && toUser != null) {
                FriendNotificationMessage notification = FriendNotificationMessage.builder()
                        .type("FRIEND_ACCEPTED")
                        .requestId(requestId)
                        .fromUserId(toUserId)
                        .fromDisplayName(toUser.getDisplayName())
                        .toUserId(friendRequest.getFromUserId())
                        .message(String.format("%s 接受了你的好友请求", toUser.getDisplayName()))
                        .timestamp(System.currentTimeMillis())
                        .build();

                webSocketService.sendFriendNotification(friendRequest.getFromUserId(), notification);
            }
        } else {
            log.info("Friend request rejected: requestId={}", requestId);
        }

        // 更新请求状态
        friendRequest.setStatus(request.getAccept() ? FriendRequestStatus.ACCEPTED : FriendRequestStatus.REJECTED);
        friendRequest.setRespondedAt(java.time.LocalDateTime.now());
        friendRequestRepository.save(friendRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendDto> getFriendList(String userId) {
        // 获取用户的所有好友关系
        List<Friendship> friendships = friendshipRepository.findByUserIdAOrUserIdB(userId, userId);

        return friendships.stream()
                .map(friendship -> {
                    // 确定好友ID（对方用户）
                    String friendId = friendship.getUserIdA().equals(userId)
                            ? friendship.getUserIdB()
                            : friendship.getUserIdA();

                    return userRepository.findById(friendId)
                            .map(user -> FriendDto.builder()
                                    .userId(user.getUserId())
                                    .username(user.getUsername())
                                    .email(user.getEmail())
                                    .displayName(user.getDisplayName())
                                    .publicKey(user.getPublicKey())
                                    .addedAt(friendship.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                                    .isOnline(connectionManager.isUserOnline(friendId))
                                    .build())
                            .orElse(null);
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FriendRequestDto> getPendingRequests(String userId) {
        List<FriendRequest> requests = friendRequestRepository
                .findByToUserIdAndStatusOrderByCreatedAtDesc(userId, FriendRequestStatus.PENDING);

        return requests.stream()
                .map(request -> {
                    User fromUser = userRepository.findById(request.getFromUserId()).orElse(null);
                    if (fromUser == null) {
                        return null;
                    }

                    return FriendRequestDto.builder()
                            .requestId(request.getId())
                            .fromUserId(request.getFromUserId())
                            .fromUsername(fromUser.getUsername())
                            .fromDisplayName(fromUser.getDisplayName())
                            .message(request.getMessage())
                            .status(request.getStatus().name())
                            .createdAt(request.getCreatedAt().toEpochSecond(ZoneOffset.UTC))
                            .respondedAt(request.getRespondedAt() != null
                                    ? request.getRespondedAt().toEpochSecond(ZoneOffset.UTC)
                                    : null)
                            .build();
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteFriend(String userId, String friendUserId) {
        // 验证用户存在
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", userId));
        userRepository.findById(friendUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "userId", friendUserId));

        // 检查是否是好友
        if (!friendshipRepository.existsByUserIdAAndUserIdB(userId, friendUserId)) {
            throw new BusinessException("NOT_FRIENDS", "用户不是好友关系");
        }

        // 删除好友关系
        friendshipRepository.deleteByUserIdAAndUserIdB(userId, friendUserId);
        log.info("Friendship deleted: userId={}, friendId={}", userId, friendUserId);

        // 通知对方
        User currentUser = userRepository.findById(userId).orElse(null);
        if (currentUser != null) {
            FriendNotificationMessage notification = FriendNotificationMessage.builder()
                    .type("FRIEND_DELETED")
                    .fromUserId(userId)
                    .fromDisplayName(currentUser.getDisplayName())
                    .toUserId(friendUserId)
                    .message(String.format("%s 删除了好友关系", currentUser.getDisplayName()))
                    .timestamp(System.currentTimeMillis())
                    .build();

            webSocketService.sendFriendNotification(friendUserId, notification);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSearchResult> searchUsers(String query) {
        List<User> users = userRepository.searchByEmailOrUsername(query);

        return users.stream()
                .map(user -> UserSearchResult.builder()
                        .userId(user.getUserId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .build())
                .collect(Collectors.toList());
    }
}
