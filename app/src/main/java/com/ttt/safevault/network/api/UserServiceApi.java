package com.ttt.safevault.network.api;

import com.ttt.safevault.dto.response.UserKeyInfoResponse;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * 用户服务 API
 * 用于获取用户公钥等，支持端到端加密分享的版本协商
 */
public interface UserServiceApi {

    /**
     * 获取用户密钥信息（用于分享时加密）
     * @param userId 用户 ID
     * @return 用户公钥信息（RSA、X25519、Ed25519）
     */
    @GET("v1/users/{userId}/keys")
    Observable<UserKeyInfoResponse> getUserKeys(@Path("userId") String userId);
}
