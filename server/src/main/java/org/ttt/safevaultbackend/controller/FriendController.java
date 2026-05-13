package org.ttt.safevaultbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ttt.safevaultbackend.dto.request.RespondFriendRequestRequest;
import org.ttt.safevaultbackend.dto.request.SendFriendRequestRequest;
import org.ttt.safevaultbackend.dto.response.FriendDto;
import org.ttt.safevaultbackend.dto.response.FriendRequestDto;
import org.ttt.safevaultbackend.dto.response.UserSearchResult;
import org.ttt.safevaultbackend.service.FriendService;
import org.ttt.safevaultbackend.service.UserService;

import java.util.List;
import java.util.Map;

/**
 * 好友系统控制器
 */
@RestController
@RequestMapping("/v1/friends")
@RequiredArgsConstructor
@Tag(name = "好友", description = "好友关系管理")
public class FriendController {

    private final FriendService friendService;
    private final UserService userService;

    /**
     * 发送好友请求
     */
    @PostMapping("/requests")
    @Operation(summary = "发送好友请求", description = "向指定用户发送好友请求")
    public ResponseEntity<Map<String, String>> sendFriendRequest(
            @Valid @RequestBody SendFriendRequestRequest request) {
        String fromUserId = userService.getCurrentUserId();
        String requestId = friendService.sendFriendRequest(request, fromUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("requestId", requestId));
    }

    /**
     * 响应好友请求
     */
    @PutMapping("/requests/{requestId}")
    @Operation(summary = "响应好友请求", description = "接受或拒绝好友请求")
    public ResponseEntity<Void> respondToFriendRequest(
            @Parameter(description = "好友请求ID")
            @PathVariable String requestId,
            @Valid @RequestBody RespondFriendRequestRequest request) {
        String toUserId = userService.getCurrentUserId();
        friendService.respondToFriendRequest(requestId, request, toUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取好友列表
     */
    @GetMapping
    @Operation(summary = "获取好友列表", description = "获取当前用户的所有好友")
    public ResponseEntity<List<FriendDto>> getFriendList() {
        String userId = userService.getCurrentUserId();
        List<FriendDto> friends = friendService.getFriendList(userId);
        return ResponseEntity.ok(friends);
    }

    /**
     * 获取待处理的好友请求
     */
    @GetMapping("/requests/pending")
    @Operation(summary = "获取待处理请求", description = "获取当前用户收到的待处理好友请求")
    public ResponseEntity<List<FriendRequestDto>> getPendingRequests() {
        String userId = userService.getCurrentUserId();
        List<FriendRequestDto> requests = friendService.getPendingRequests(userId);
        return ResponseEntity.ok(requests);
    }

    /**
     * 删除好友
     */
    @DeleteMapping("/{friendUserId}")
    @Operation(summary = "删除好友", description = "删除指定的好友关系")
    public ResponseEntity<Void> deleteFriend(
            @Parameter(description = "要删除的好友用户ID")
            @PathVariable String friendUserId) {
        String userId = userService.getCurrentUserId();
        friendService.deleteFriend(userId, friendUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * 搜索用户
     */
    @GetMapping("/search")
    @Operation(summary = "搜索用户", description = "通过用户名或邮箱搜索用户")
    public ResponseEntity<List<UserSearchResult>> searchUsers(
            @Parameter(description = "搜索查询（用户名或邮箱）")
            @RequestParam String query) {
        List<UserSearchResult> results = friendService.searchUsers(query);
        return ResponseEntity.ok(results);
    }
}
