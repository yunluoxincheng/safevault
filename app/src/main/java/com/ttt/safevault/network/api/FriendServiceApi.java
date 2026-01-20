package com.ttt.safevault.network.api;

import com.ttt.safevault.dto.request.RespondFriendRequestRequest;
import com.ttt.safevault.dto.request.SendFriendRequestRequest;
import com.ttt.safevault.dto.response.FriendDto;
import com.ttt.safevault.dto.response.FriendRequestDto;
import com.ttt.safevault.dto.response.FriendRequestResponse;
import com.ttt.safevault.dto.response.UserSearchResult;

import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 好友系统API接口
 */
public interface FriendServiceApi {

    /**
     * 发送好友请求
     * @param request 好友请求信息
     * @return 返回包含requestId的响应
     */
    @POST("v1/friends/requests")
    Observable<FriendRequestResponse> sendFriendRequest(
            @Body SendFriendRequestRequest request
    );

    /**
     * 响应好友请求（接受或拒绝）
     * @param requestId 好友请求ID
     * @param request 响应信息（accept字段）
     * @return 无返回数据
     */
    @PUT("v1/friends/requests/{requestId}")
    Observable<Void> respondToFriendRequest(
            @Path("requestId") String requestId,
            @Body RespondFriendRequestRequest request
    );

    /**
     * 获取好友列表
     * @return 好友列表
     */
    @GET("v1/friends")
    Observable<List<FriendDto>> getFriendList();

    /**
     * 获取待处理的好友请求
     * @return 待处理的好友请求列表
     */
    @GET("v1/friends/requests/pending")
    Observable<List<FriendRequestDto>> getPendingRequests();

    /**
     * 删除好友
     * @param friendUserId 要删除的好友用户ID
     * @return 无返回数据
     */
    @DELETE("v1/friends/{friendUserId}")
    Observable<Void> deleteFriend(
            @Path("friendUserId") String friendUserId
    );

    /**
     * 搜索用户（通过用户名或邮箱）
     * @param query 搜索查询
     * @return 匹配的用户列表
     */
    @GET("v1/friends/search")
    Observable<List<UserSearchResult>> searchUsers(
            @Query("query") String query
    );
}
