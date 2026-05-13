package org.ttt.safevaultbackend.service;

import org.ttt.safevaultbackend.dto.request.RespondFriendRequestRequest;
import org.ttt.safevaultbackend.dto.request.SendFriendRequestRequest;
import org.ttt.safevaultbackend.dto.response.FriendDto;
import org.ttt.safevaultbackend.dto.response.FriendRequestDto;
import org.ttt.safevaultbackend.dto.response.UserSearchResult;

import java.util.List;

/**
 * 好友系统服务接口
 */
public interface FriendService {

    /**
     * 发送好友请求
     *
     * @param request 好友请求（包含目标用户ID和可选消息）
     * @param fromUserId 发送者用户ID（从JWT获取）
     * @return 创建的好友请求ID
     */
    String sendFriendRequest(SendFriendRequestRequest request, String fromUserId);

    /**
     * 响应好友请求（接受或拒绝）
     *
     * @param requestId 好友请求ID
     * @param request 响应请求（包含accept标志）
     * @param toUserId 当前用户ID（接收者，从JWT获取）
     */
    void respondToFriendRequest(String requestId, RespondFriendRequestRequest request, String toUserId);

    /**
     * 获取用户的好友列表
     *
     * @param userId 用户ID（从JWT获取）
     * @return 好友列表
     */
    List<FriendDto> getFriendList(String userId);

    /**
     * 获取用户的待处理好友请求
     *
     * @param userId 用户ID（从JWT获取）
     * @return 待处理的好友请求列表
     */
    List<FriendRequestDto> getPendingRequests(String userId);

    /**
     * 删除好友
     *
     * @param userId 当前用户ID（从JWT获取）
     * @param friendUserId 要删除的好友用户ID
     */
    void deleteFriend(String userId, String friendUserId);

    /**
     * 搜索用户
     *
     * @param query 搜索查询（用户名或邮箱）
     * @return 搜索结果列表
     */
    List<UserSearchResult> searchUsers(String query);
}
