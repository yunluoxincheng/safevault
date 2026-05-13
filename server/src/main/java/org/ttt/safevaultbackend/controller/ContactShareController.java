package org.ttt.safevaultbackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ttt.safevaultbackend.dto.request.CreateContactShareRequest;
import org.ttt.safevaultbackend.dto.response.*;
import org.ttt.safevaultbackend.service.ContactShareService;
import org.ttt.safevaultbackend.service.UserService;

import java.util.List;

/**
 * 联系人分享控制器
 * 仅支持好友间的密码分享
 */
@RestController
@RequestMapping("/v1/shares")
@RequiredArgsConstructor
@Tag(name = "联系人分享", description = "联系人密码分享管理")
public class ContactShareController {

    private final ContactShareService contactShareService;
    private final UserService userService;

    @PostMapping("/contact")
    @Operation(summary = "创建联系人分享", description = "向好友分享密码（必须是已接受的好友关系）")
    public ResponseEntity<ContactShareResponse> createContactShare(@Valid @RequestBody CreateContactShareRequest request) {
        String userId = userService.getCurrentUserId();
        ContactShareResponse response = contactShareService.createContactShare(request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{shareId}")
    @Operation(summary = "接收分享", description = "通过分享ID获取分享的密码详情")
    public ResponseEntity<ReceivedContactShareResponse> receiveShare(@PathVariable String shareId) {
        String userId = userService.getCurrentUserId();
        ReceivedContactShareResponse response = contactShareService.receiveShare(shareId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{shareId}/accept")
    @Operation(summary = "接受分享", description = "接受并保存分享的密码")
    public ResponseEntity<AcceptShareResponse> acceptShare(@PathVariable String shareId) {
        String userId = userService.getCurrentUserId();
        AcceptShareResponse response = contactShareService.acceptShare(shareId, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{shareId}")
    @Operation(summary = "撤销分享", description = "撤销指定的密码分享")
    public ResponseEntity<Void> revokeShare(@PathVariable String shareId) {
        String userId = userService.getCurrentUserId();
        contactShareService.revokeShare(shareId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sent")
    @Operation(summary = "获取发送的分享", description = "获取当前用户发送的所有分享")
    public ResponseEntity<List<SentContactShareResponse>> getSentShares() {
        String userId = userService.getCurrentUserId();
        List<SentContactShareResponse> response = contactShareService.getSentShares(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/received")
    @Operation(summary = "获取接收的分享", description = "获取当前用户接收的所有分享")
    public ResponseEntity<List<ReceivedContactShareResponse>> getReceivedShares() {
        String userId = userService.getCurrentUserId();
        List<ReceivedContactShareResponse> response = contactShareService.getReceivedShares(userId);
        return ResponseEntity.ok(response);
    }
}
